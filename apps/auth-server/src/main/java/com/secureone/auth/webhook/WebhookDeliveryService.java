package com.secureone.auth.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.tenant.RlsScope;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Webhook delivery with retries. An event is stored first (an outbox row) and sent once the change that caused it
 * has committed. A delivery the receiver does not accept (no answer, or not 2xx) is tried again after 1 minute,
 * 5 minutes, 30 minutes, 2, 6, 12 and 24 hours: 8 tries over about two days, then it is marked failed and can be
 * sent again from the console. Several servers share the work: each claims due deliveries for two minutes.
 *
 * <p>Each request is signed: {@code X-SecureOne-Signature-256: t=<unix time>,v1=<hex HMAC-SHA256 of "t.body">}
 * (two {@code v1} values while an old secret is still valid after a rotation), and, for receivers built before
 * that header, {@code X-SecureOne-Signature: sha256=<hex HMAC-SHA256 of the body>}. Receivers should refuse a
 * timestamp more than five minutes old (replays) and use {@code X-SecureOne-Delivery} to ignore repeats.
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int MAX_ATTEMPTS = 8;
    /** Wait after try n (1-based) before the next one. */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6), Duration.ofHours(12),
            Duration.ofHours(24));
    private static final int BATCH = 50;

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final SecretProtector secrets;
    private final ObjectMapper objectMapper;
    private final RestClient http;
    private final WebhookTargetPolicy targets;
    private final JdbcTemplate jdbc;
    /** Sends right after a commit without waiting for the next scheduled round; one round at a time. */
    private final ExecutorService kicker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webhook-dispatch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean kickQueued = new AtomicBoolean();

    public WebhookDeliveryService(
            WebhookDeliveryRepository deliveries,
            WebhookEndpointRepository endpoints,
            SecretProtector secrets,
            ObjectMapper objectMapper,
            WebhookTargetPolicy targets,
            JdbcTemplate jdbc) {
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.secrets = secrets;
        this.objectMapper = objectMapper;
        this.targets = targets;
        this.jdbc = jdbc;
        // Bounded time per delivery, and no redirects (a redirect could point at an internal address).
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @PreDestroy
    void stop() {
        kicker.shutdownNow();
    }

    /** Stores the delivery; it is sent after the surrounding transaction commits. */
    @Transactional
    public WebhookDelivery enqueue(UUID endpointId, String eventType, Map<String, Object> payload) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEndpointId(endpointId);
        delivery.setEventType(eventType);
        delivery.setPayload(payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>());
        delivery.setStatus("PENDING");
        delivery.setNextAttemptAt(Instant.now());
        WebhookDelivery saved = deliveries.save(delivery);
        kickAfterCommit();
        return saved;
    }

    /** Sends the due deliveries now (after the current transaction, if any, commits). */
    public void kickAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kick();
                }
            });
        } else {
            kick();
        }
    }

    private void kick() {
        if (kickQueued.compareAndSet(false, true)) {
            try {
                kicker.execute(() -> {
                    kickQueued.set(false);
                    dispatchDue();
                });
            } catch (RuntimeException e) {
                kickQueued.set(false); // shutting down: the scheduled round picks them up
            }
        }
    }

    /** Every 15 seconds: retries that are due, and anything a kick missed (another server, a restart). */
    @Scheduled(fixedDelayString = "${secureone.webhooks.dispatch-interval:15s}", initialDelay = 20_000)
    public void dispatchDue() {
        try {
            List<UUID> claimed;
            do {
                claimed = RlsScope.asPlatform(() -> jdbc.queryForList("""
                        UPDATE platform.webhook_delivery SET next_attempt_at = now() + interval '2 minutes'
                        WHERE id IN (SELECT id FROM platform.webhook_delivery
                                     WHERE status IN ('PENDING', 'RETRYING') AND next_attempt_at <= now()
                                     ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED)
                        RETURNING id
                        """, UUID.class, BATCH));
                for (UUID id : claimed) {
                    RlsScope.runAsPlatform(() -> attempt(id));
                }
            } while (claimed.size() == BATCH);
        } catch (RuntimeException e) {
            log.warn("Webhook dispatch round failed: {}", e.getMessage());
        }
    }

    private void attempt(UUID deliveryId) {
        WebhookDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        WebhookEndpoint endpoint = endpoints.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isEnabled()) {
            delivery.setStatus("FAILED");
            delivery.setLastError("The webhook is turned off.");
            delivery.setNextAttemptAt(null);
            deliveries.save(delivery);
            return;
        }
        Result result = send(endpoint, delivery, delivery.getAttempts() + 1);
        record(delivery, result, true);
    }

    /** Sends a test event now and waits for the answer (console "Send test"). Not retried. */
    public Map<String, Object> sendTest(WebhookEndpoint endpoint) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEndpointId(endpoint.getId());
        delivery.setEventType("webhook.test");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Test event from SecureOne. Your endpoint received it.");
        delivery.setPayload(payload);
        delivery.setStatus("SENDING");
        delivery = deliveries.save(delivery);
        Result result = send(endpoint, delivery, 1);
        record(delivery, result, false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", result.ok());
        out.put("responseCode", result.code());
        out.put("error", result.error());
        out.put("deliveryId", delivery.getId().toString());
        return out;
    }

    /** Queues a delivery again from the start (console "Send again"). */
    public void redeliver(WebhookDelivery delivery) {
        delivery.setStatus("PENDING");
        delivery.setAttempts(0);
        delivery.setLastError(null);
        delivery.setNextAttemptAt(Instant.now());
        deliveries.save(delivery);
        kickAfterCommit();
    }

    record Result(boolean ok, Integer code, String error) {}

    private void record(WebhookDelivery delivery, Result result, boolean retry) {
        int attempts = delivery.getAttempts() + 1;
        delivery.setAttempts(attempts);
        delivery.setResponseCode(result.code());
        if (result.ok()) {
            delivery.setStatus("DELIVERED");
            delivery.setDeliveredAt(Instant.now());
            delivery.setLastError(null);
            delivery.setNextAttemptAt(null);
        } else {
            delivery.setLastError(result.error() != null && result.error().length() > 500
                    ? result.error().substring(0, 500) : result.error());
            if (retry && attempts < MAX_ATTEMPTS) {
                delivery.setStatus("RETRYING");
                delivery.setNextAttemptAt(Instant.now().plus(BACKOFF.get(Math.min(attempts, BACKOFF.size()) - 1)));
            } else {
                delivery.setStatus("FAILED");
                delivery.setNextAttemptAt(null);
                if (retry) {
                    log.warn("Webhook delivery {} to endpoint {} failed after {} tries: {}", delivery.getId(),
                            delivery.getEndpointId(), attempts, result.error());
                }
            }
        }
        deliveries.save(delivery);
    }

    private Result send(WebhookEndpoint endpoint, WebhookDelivery delivery, int attempt) {
        String blocked = targets.deliveryProblem(endpoint.getUrl());
        if (blocked != null) {
            return new Result(false, null, "Not sent: " + blocked);
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(buildEnvelope(delivery));
        } catch (Exception ex) {
            return new Result(false, null, "The event could not be written as JSON.");
        }
        List<String> signingSecrets = new ArrayList<>();
        try {
            signingSecrets.add(secrets.decrypt(endpoint.getSecretEncrypted()));
            if (endpoint.getPreviousSecretEncrypted() != null && endpoint.getPreviousSecretExpiresAt() != null
                    && endpoint.getPreviousSecretExpiresAt().isAfter(Instant.now())) {
                signingSecrets.add(secrets.decrypt(endpoint.getPreviousSecretEncrypted()));
            }
        } catch (RuntimeException e) {
            return new Result(false, null, "The signing secret cannot be read; rotate it.");
        }
        long timestamp = Instant.now().getEpochSecond();
        try {
            var response = http.post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-SecureOne-Event", delivery.getEventType())
                    .header("X-SecureOne-Delivery", delivery.getId().toString())
                    .header("X-SecureOne-Attempt", String.valueOf(attempt))
                    .header("X-SecureOne-Timestamp", String.valueOf(timestamp))
                    .header("X-SecureOne-Signature-256", signatureHeader(timestamp, body, signingSecrets))
                    .header("X-SecureOne-Signature", sign(body, signingSecrets.get(0)))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return new Result(true, response.getStatusCode().value(), null);
        } catch (RestClientResponseException ex) {
            return new Result(false, ex.getStatusCode().value(), "The endpoint answered " + ex.getStatusCode().value() + ".");
        } catch (Exception ex) {
            return new Result(false, null, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    static String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** {@code t=<time>,v1=<sig>[,v1=<sig with the previous secret>]}. */
    static String signatureHeader(long timestamp, String body, List<String> signingSecrets) {
        StringBuilder out = new StringBuilder("t=").append(timestamp);
        for (String secret : signingSecrets) {
            out.append(",v1=").append(hmacHex(timestamp + "." + body, secret));
        }
        return out.toString();
    }

    static String sign(String payload, String secret) {
        return "sha256=" + hmacHex(payload, secret);
    }

    private static String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 not available", ex);
        }
    }

    private static Map<String, Object> buildEnvelope(WebhookDelivery delivery) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", delivery.getId().toString());
        envelope.put("eventType", delivery.getEventType());
        envelope.put("createdAt", delivery.getCreatedAt().toString());
        envelope.put("data", delivery.getPayload());
        return envelope;
    }
}
