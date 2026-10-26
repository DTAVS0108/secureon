package com.secureone.auth.audit.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.audit.AuditLog;
import com.secureone.auth.audit.AuditRecordedEvent;
import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.webhook.WebhookTargetPolicy;
import java.time.Duration;
import java.time.Instant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

/**
 * Log streams (Platform settings › Logs & streaming): every audit entry, from every tenant, is sent as it happens
 * to the security team's SIEM or log service: an HTTPS endpoint taking JSON (Datadog, Elastic, Sumo Logic, a
 * custom collector) or a Splunk HTTP Event Collector. Like Auth0 log streams. Also holds how long the audit log
 * and sign-in history are kept ({@link LogRetentionJob}).
 */
@Service
public class LogStreamService {

    private static final Logger log = LoggerFactory.getLogger(LogStreamService.class);

    public static final String SETTINGS_KEY = "log_streams";
    public static final Set<String> FORMATS = Set.of("json", "splunk");
    static final int MAX_STREAMS = 5;
    static final int QUEUE_CAPACITY = 10_000;
    static final int BATCH_SIZE = 500;
    static final int MAX_ATTEMPTS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformSettingsService settings;
    private final SecretProtector secrets;
    private final WebhookTargetPolicy targets;
    private final RestClient http;
    /** Last delivery per stream on this server: shown in the console. */
    private final Map<String, Map<String, Object>> status = new ConcurrentHashMap<>();
    private final BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    /** Per stream, the entries it could not take yet (touched by the flush thread only). */
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private ScheduledExecutorService flusher;

    public LogStreamService(PlatformSettingsService settings, SecretProtector secrets, WebhookTargetPolicy targets) {
        this.settings = settings;
        this.secrets = secrets;
        this.targets = targets;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("auditRetentionDays", 365);
        out.put("signInHistoryRetentionDays", 365);
        out.put("streams", List.of());
        return out;
    }

    // ---- Settings ------------------------------------------------------------------------------------------------

    public Map<String, Object> getForAdmin() {
        Map<String, Object> stored = stored();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("auditRetentionDays", stored.get("auditRetentionDays"));
        out.put("signInHistoryRetentionDays", stored.get("signInHistoryRetentionDays"));
        List<Map<String, Object>> streams = new ArrayList<>();
        for (Map<String, Object> s : streams(stored)) {
            Map<String, Object> view = new LinkedHashMap<>(s);
            view.put("headerValueSet", !str(s.get("headerValueEncrypted")).isBlank());
            view.remove("headerValueEncrypted");
            view.put("lastDelivery", status.get(str(s.get("id"))));
            streams.add(view);
        }
        out.put("streams", streams);
        return out;
    }

    /** A stream's blank {@code headerValue} keeps the stored one. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> save(Map<String, Object> body) {
        Map<String, Object> stored = stored();
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("auditRetentionDays", days(body.get("auditRetentionDays"), 365));
        next.put("signInHistoryRetentionDays", days(body.get("signInHistoryRetentionDays"), 365));
        Map<String, Map<String, Object>> previous = new LinkedHashMap<>();
        streams(stored).forEach(s -> previous.put(str(s.get("id")), s));
        List<Map<String, Object>> out = new ArrayList<>();
        Object raw = body.get("streams");
        if (raw instanceof List<?> list) {
            if (list.size() > MAX_STREAMS) {
                throw new IllegalArgumentException("At most " + MAX_STREAMS + " log streams.");
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> in = (Map<String, Object>) m;
                String id = str(in.get("id")).isBlank() ? UUID.randomUUID().toString() : str(in.get("id"));
                String name = str(in.get("name")).trim();
                if (name.isBlank() || name.length() > 80) {
                    throw new IllegalArgumentException("Each log stream needs a name (up to 80 characters).");
                }
                String url = targets.validateForRegistration(str(in.get("url")).trim());
                if (!url.startsWith("https://")) {
                    throw new IllegalArgumentException("Log stream " + name + ": the address must use https://.");
                }
                String format = FORMATS.contains(str(in.get("format"))) ? str(in.get("format")) : "json";
                String headerName = str(in.get("headerName")).trim();
                if (!headerName.isBlank() && !headerName.matches("[A-Za-z0-9-]{1,64}")) {
                    throw new IllegalArgumentException("Log stream " + name + ": the header name is not valid.");
                }
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("id", id);
                s.put("name", name);
                s.put("url", url);
                s.put("format", format);
                s.put("enabled", !Boolean.FALSE.equals(in.get("enabled")));
                s.put("headerName", headerName);
                String headerValue = str(in.get("headerValue"));
                String kept = previous.containsKey(id) ? str(previous.get(id).get("headerValueEncrypted")) : "";
                s.put("headerValueEncrypted", !headerValue.isBlank() ? secrets.encrypt(headerValue.trim()) : kept);
                out.add(s);
            }
        }
        next.put("streams", out);
        settings.saveMap(SETTINGS_KEY, next);
        cached = null;
        return getForAdmin();
    }

    private static int days(Object raw, int fallback) {
        int value = raw instanceof Number n ? n.intValue() : fallback;
        if (value < 30 || value > 3650) {
            throw new IllegalArgumentException("Keep logs for 30 to 3650 days.");
        }
        return value;
    }

    public int auditRetentionDays() {
        return stored().get("auditRetentionDays") instanceof Number n ? n.intValue() : 365;
    }

    public int signInHistoryRetentionDays() {
        return stored().get("signInHistoryRetentionDays") instanceof Number n ? n.intValue() : 365;
    }

    private volatile Map<String, Object> cached;
    private volatile long cachedAt;

    private Map<String, Object> stored() {
        Map<String, Object> c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < 5_000) {
            return c;
        }
        Map<String, Object> s = RlsScope.asPlatform(() -> settings.get(SETTINGS_KEY));
        c = s != null && !s.isEmpty() ? s : defaults();
        cached = c;
        cachedAt = System.currentTimeMillis();
        return c;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> streams(Map<String, Object> config) {
        return config.get("streams") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    // ---- Delivery ------------------------------------------------------------------------------------------------

    /**
     * Queues the entry; a dedicated thread sends queued entries in batches every two seconds. Never blocks the
     * request that wrote the audit entry, and never ties up the shared async pool while a SIEM is slow or down. When
     * the queue is full (the SIEM has been unreachable for a long time) the oldest entries are dropped.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAudit(AuditRecordedEvent event) {
        if (streams(stored()).stream().noneMatch(s -> Boolean.TRUE.equals(s.get("enabled")))) {
            return;
        }
        Map<String, Object> entry = toJson(event.log());
        while (!queue.offer(entry)) {
            if (queue.poll() != null) {
                dropped.incrementAndGet();
            }
        }
    }

    @PostConstruct
    void start() {
        flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-stream-flush");
            t.setDaemon(true);
            return t;
        });
        flusher.scheduleWithFixedDelay(this::flushSafely, 2, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (flusher != null) {
            flusher.shutdown();
            try {
                flusher.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void flushSafely() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("Log stream delivery failed: {}", e.getMessage());
        }
    }

    /**
     * One tick: each enabled stream first retries the batch it could not send last time (dropped after
     * {@code MAX_ATTEMPTS} tries), then takes the new batch from the queue. No waiting between tries: the next
     * tick is the retry.
     */
    void flush() {
        List<Map<String, Object>> active = streams(stored()).stream()
                .filter(s -> Boolean.TRUE.equals(s.get("enabled")))
                .toList();
        Set<String> activeIds = new HashSet<>();
        active.forEach(s -> activeIds.add(str(s.get("id"))));
        pending.keySet().retainAll(activeIds);
        List<Map<String, Object>> fresh = new ArrayList<>();
        queue.drainTo(fresh, BATCH_SIZE);
        long lost = dropped.getAndSet(0);
        if (lost > 0) {
            log.warn("Log streams fell behind; {} audit entries were not streamed", lost);
        }
        for (Map<String, Object> stream : active) {
            String id = str(stream.get("id"));
            Pending retry = pending.get(id);
            if (retry != null) {
                if (send(stream, retry.entries)) {
                    pending.remove(id);
                } else if (++retry.attempts >= MAX_ATTEMPTS) {
                    pending.remove(id);
                    log.warn("Log stream {} dropped {} audit entries after {} tries", stream.get("name"),
                            retry.entries.size(), MAX_ATTEMPTS);
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }
            Pending stillPending = pending.get(id);
            if (stillPending != null) {
                // Still failing: hold the new entries behind the old ones, in order, up to four batches.
                if (stillPending.entries.size() < BATCH_SIZE * 4) {
                    stillPending.entries.addAll(fresh);
                }
            } else if (!send(stream, fresh)) {
                pending.put(id, new Pending(new ArrayList<>(fresh)));
            }
        }
    }

    private static final class Pending {
        final List<Map<String, Object>> entries;
        int attempts = 1;

        Pending(List<Map<String, Object>> entries) {
            this.entries = entries;
        }
    }

    /** Sends one test entry to a saved stream, so the console can check the connection. */
    public Map<String, Object> test(String streamId) {
        Map<String, Object> stream = streams(stored()).stream()
                .filter(s -> streamId.equals(str(s.get("id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Log stream not found: " + streamId));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", UUID.randomUUID().toString());
        entry.put("time", Instant.now().toString());
        entry.put("action", "log_stream.test");
        entry.put("result", "success");
        entry.put("source", "secureone");
        send(stream, List.of(entry));
        return status.get(streamId);
    }

    /**
     * One POST with the batch: a JSON array for JSON streams, concatenated event objects for a Splunk HTTP Event
     * Collector (its batch format). Returns whether the stream took it.
     */
    private boolean send(Map<String, Object> stream, List<Map<String, Object>> entries) {
        String id = str(stream.get("id"));
        String url = str(stream.get("url"));
        String problem = targets.deliveryProblem(url);
        if (problem != null) {
            record(id, false, 0, problem);
            return false;
        }
        String body;
        try {
            body = body(str(stream.get("format")), entries);
        } catch (JsonProcessingException e) {
            record(id, false, 0, "entries could not be written as JSON: " + e.getOriginalMessage());
            return false;
        }
        String headerName = str(stream.get("headerName"));
        String headerValue = "";
        if (!headerName.isBlank() && !str(stream.get("headerValueEncrypted")).isBlank()) {
            try {
                headerValue = secrets.decrypt(str(stream.get("headerValueEncrypted")));
            } catch (RuntimeException e) {
                record(id, false, 0, "the saved header value cannot be read; save it again");
                return false;
            }
        }
        try {
            var request = http.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body);
            if (!headerName.isBlank() && !headerValue.isBlank()) {
                request = request.header(headerName, headerValue);
            }
            int code = request.retrieve().toBodilessEntity().getStatusCode().value();
            record(id, true, code, null);
            return true;
        } catch (RuntimeException e) {
            record(id, false, 0, e.getMessage());
            log.warn("Log stream {} could not take {} audit entries: {}", stream.get("name"), entries.size(),
                    e.getMessage());
            return false;
        }
    }

    static String body(String format, List<Map<String, Object>> entries) throws JsonProcessingException {
        if (!"splunk".equals(format)) {
            return JSON.writeValueAsString(entries);
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> entry : entries) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("time", Instant.parse(str(entry.get("time"))).getEpochSecond());
            event.put("source", "secureone");
            event.put("sourcetype", "secureone:audit");
            event.put("event", entry);
            out.append(JSON.writeValueAsString(event));
        }
        return out.toString();
    }

    private void record(String id, boolean ok, int code, String error) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("at", Instant.now().toString());
        s.put("ok", ok);
        s.put("status", code);
        s.put("error", error != null && error.length() > 300 ? error.substring(0, 300) : error);
        status.put(id, s);
    }

    static Map<String, Object> toJson(AuditLog l) {
        Map<String, Object> meta = l.getMetadata() != null ? l.getMetadata() : Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", l.getId() != null ? l.getId().toString() : null);
        out.put("time", (l.getCreatedAt() != null ? l.getCreatedAt() : Instant.now()).toString());
        out.put("action", l.getAction());
        out.put("result", meta.getOrDefault("result", "success"));
        out.put("actor", meta.get("actorEmail"));
        out.put("target", meta.get("targetLabel"));
        out.put("targetType", l.getTargetType());
        out.put("targetId", l.getTargetId() != null ? l.getTargetId().toString() : null);
        out.put("tenantId", l.getTenantId() != null ? l.getTenantId().toString() : null);
        out.put("applicationId", l.getApplicationId() != null ? l.getApplicationId().toString() : null);
        out.put("ip", l.getIp());
        out.put("userAgent", l.getUserAgent());
        out.put("requestId", meta.get("requestId"));
        out.put("source", "secureone");
        return out;
    }

    private static String str(Object value) {
        return value != null ? value.toString() : "";
    }
}
