package com.secureone.auth.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.notify.SecretProtector;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookDeliveryRepository deliveries;
    private final SecretProtector secrets;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public WebhookDeliveryService(
            WebhookDeliveryRepository deliveries, SecretProtector secrets, ObjectMapper objectMapper) {
        this.deliveries = deliveries;
        this.secrets = secrets;
        this.objectMapper = objectMapper;
        this.http = RestClient.builder().build();
    }

    @Transactional
    public WebhookDelivery enqueue(UUID endpointId, String eventType, Map<String, Object> payload) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEndpointId(endpointId);
        delivery.setEventType(eventType);
        delivery.setPayload(payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>());
        delivery.setStatus("PENDING");
        return deliveries.save(delivery);
    }

    @Async
    public void deliverAsync(WebhookEndpoint endpoint, WebhookDelivery delivery) {
        deliver(endpoint, delivery);
    }

    void deliver(WebhookEndpoint endpoint, WebhookDelivery delivery) {
        String secret = secrets.decrypt(endpoint.getSecretEncrypted());
        String body;
        try {
            body = objectMapper.writeValueAsString(buildEnvelope(delivery));
        } catch (Exception ex) {
            markFailed(delivery, null);
            log.warn("Webhook payload serialization failed for delivery {}", delivery.getId(), ex);
            return;
        }
        String signature = sign(body, secret);
        try {
            var response = http.post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-SecureOne-Signature", signature)
                    .header("X-SecureOne-Event", delivery.getEventType())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            delivery.setStatus("DELIVERED");
            delivery.setResponseCode(response.getStatusCode().value());
        } catch (Exception ex) {
            markFailed(delivery, null);
            log.warn("Webhook delivery failed for endpoint {} event {}", endpoint.getId(), delivery.getEventType(), ex);
            return;
        }
        deliveries.save(delivery);
    }

    static String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
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

    private void markFailed(WebhookDelivery delivery, Integer responseCode) {
        delivery.setStatus("FAILED");
        delivery.setResponseCode(responseCode);
        deliveries.save(delivery);
    }

}
