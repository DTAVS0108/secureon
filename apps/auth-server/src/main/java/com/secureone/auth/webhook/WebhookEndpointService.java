package com.secureone.auth.webhook;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.notify.SecretProtector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WebhookEndpointService {

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final SecretProtector secrets;
    private final WebhookTargetPolicy targets;

    public WebhookEndpointService(
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            SecretProtector secrets,
            WebhookTargetPolicy targets) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.secrets = secrets;
        this.targets = targets;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return endpoints.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** One application's own endpoints (the tenant's also receive its events, but are managed at tenant level). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForApplication(UUID tenantId, UUID applicationId) {
        return endpoints.findByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** An endpoint of this application (for the application's administrators); others are not found. */
    @Transactional(readOnly = true)
    public void requireApplicationEndpoint(UUID tenantId, UUID applicationId, UUID id) {
        if (!applicationId.equals(requireEndpoint(tenantId, id).getApplicationId())) {
            throw new ResourceNotFoundException("Webhook endpoint not found: " + id);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, UUID id) {
        return toResponse(requireEndpoint(tenantId, id));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.application.ApplicationRepository applications;

    /** An endpoint may be limited to one application of its own tenant. */
    private UUID applicationOf(UUID tenantId, Object raw) {
        UUID applicationId = parseUuid(raw);
        if (applicationId != null && applications != null && applications.findById(applicationId)
                .filter(a -> tenantId.equals(a.getTenantId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The application is not one of this tenant's.");
        }
        return applicationId;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDeliveries(UUID tenantId, UUID endpointId) {
        requireEndpoint(tenantId, endpointId);
        return deliveries.findTop100ByEndpointIdOrderByCreatedAtDesc(endpointId).stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    public Map<String, Object> create(UUID tenantId, Map<String, Object> body) {
        String url = targets.validateForRegistration(requiredString(body.get("url"), "url"));
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenantId(tenantId);
        endpoint.setApplicationId(applicationOf(tenantId, body.get("applicationId")));
        endpoint.setUrl(url);
        String rawSecret = WebhookDeliveryService.generateSecret();
        endpoint.setSecretEncrypted(secrets.encrypt(rawSecret));
        endpoint.setEvents(parseEvents(body.get("events")));
        endpoint.setEnabled(parseEnabled(body.get("enabled"), true));
        endpoint = endpoints.save(endpoint);
        Map<String, Object> out = toResponse(endpoint);
        out.put("secret", rawSecret);
        return out;
    }

    public Map<String, Object> update(UUID tenantId, UUID id, Map<String, Object> body) {
        WebhookEndpoint endpoint = requireEndpoint(tenantId, id);
        if (body.containsKey("url")) {
            endpoint.setUrl(targets.validateForRegistration(requiredString(body.get("url"), "url")));
        }
        if (body.containsKey("applicationId")) {
            endpoint.setApplicationId(applicationOf(tenantId, body.get("applicationId")));
        }
        if (body.containsKey("events")) {
            endpoint.setEvents(parseEvents(body.get("events")));
        }
        if (body.containsKey("enabled")) {
            endpoint.setEnabled(parseEnabled(body.get("enabled"), endpoint.isEnabled()));
        }
        return toResponse(endpoints.save(endpoint));
    }

    /**
     * New signing secret, shown once. The old one keeps signing too (a second {@code v1} signature) for
     * {@code keepOldHours} (0 to 168), so receivers can switch without missing events.
     */
    public Map<String, Object> rotateSecret(UUID tenantId, UUID id, int keepOldHours) {
        if (keepOldHours < 0 || keepOldHours > 168) {
            throw new IllegalArgumentException("Keep the old secret for 0 to 168 hours.");
        }
        WebhookEndpoint endpoint = requireEndpoint(tenantId, id);
        String rawSecret = WebhookDeliveryService.generateSecret();
        if (keepOldHours > 0) {
            endpoint.setPreviousSecretEncrypted(endpoint.getSecretEncrypted());
            endpoint.setPreviousSecretExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(keepOldHours)));
        } else {
            endpoint.setPreviousSecretEncrypted(null);
            endpoint.setPreviousSecretExpiresAt(null);
        }
        endpoint.setSecretEncrypted(secrets.encrypt(rawSecret));
        Map<String, Object> out = toResponse(endpoints.save(endpoint));
        out.put("secret", rawSecret);
        return out;
    }

    /** Sends a test event now and returns what the endpoint answered. */
    public Map<String, Object> sendTest(UUID tenantId, UUID id) {
        return delivery.sendTest(requireEndpoint(tenantId, id));
    }

    /** Queues one past delivery again, from the first try. */
    public Map<String, Object> redeliver(UUID tenantId, UUID endpointId, UUID deliveryId) {
        requireEndpoint(tenantId, endpointId);
        WebhookDelivery row = deliveries.findById(deliveryId)
                .filter(d -> endpointId.equals(d.getEndpointId()))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery not found: " + deliveryId));
        delivery.redeliver(row);
        return toDeliveryResponse(row);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private WebhookDeliveryService delivery;

    /** Sets the signing secret to a value the operator supplies (configuration files). */
    public void setSecret(UUID tenantId, UUID id, String rawSecret) {
        WebhookEndpoint endpoint = requireEndpoint(tenantId, id);
        endpoint.setSecretEncrypted(secrets.encrypt(rawSecret));
        endpoints.save(endpoint);
    }

    @Transactional(readOnly = true)
    public boolean secretMatches(UUID tenantId, UUID id, String rawSecret) {
        return rawSecret != null && rawSecret.equals(secrets.decrypt(requireEndpoint(tenantId, id).getSecretEncrypted()));
    }

    public void delete(UUID tenantId, UUID id) {
        WebhookEndpoint endpoint = requireEndpoint(tenantId, id);
        endpoints.delete(endpoint);
    }

    WebhookEndpoint requireEndpoint(UUID tenantId, UUID id) {
        return endpoints.findById(id)
                .filter(row -> tenantId.equals(row.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + id));
    }

    private Map<String, Object> toResponse(WebhookEndpoint endpoint) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", endpoint.getId().toString());
        out.put("tenantId", endpoint.getTenantId().toString());
        out.put("applicationId", endpoint.getApplicationId() != null ? endpoint.getApplicationId().toString() : null);
        out.put("url", endpoint.getUrl());
        out.put("events", endpoint.getEvents());
        out.put("enabled", endpoint.isEnabled());
        out.put("createdAt", endpoint.getCreatedAt().toString());
        boolean overlap = endpoint.getPreviousSecretExpiresAt() != null
                && endpoint.getPreviousSecretExpiresAt().isAfter(java.time.Instant.now());
        out.put("previousSecretValidUntil", overlap ? endpoint.getPreviousSecretExpiresAt().toString() : null);
        return out;
    }

    private Map<String, Object> toDeliveryResponse(WebhookDelivery delivery) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", delivery.getId().toString());
        out.put("endpointId", delivery.getEndpointId().toString());
        out.put("eventType", delivery.getEventType());
        out.put("payload", delivery.getPayload());
        out.put("status", delivery.getStatus());
        out.put("responseCode", delivery.getResponseCode());
        out.put("createdAt", delivery.getCreatedAt().toString());
        out.put("attempts", delivery.getAttempts());
        out.put("lastError", delivery.getLastError());
        out.put("nextAttemptAt", delivery.getNextAttemptAt() != null ? delivery.getNextAttemptAt().toString() : null);
        out.put("deliveredAt", delivery.getDeliveredAt() != null ? delivery.getDeliveredAt().toString() : null);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseEvents(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> events = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    events.add(item.toString().trim());
                }
            }
            WebhookTargetPolicy.validateEvents(events);
            return events;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "events must be an array.");
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID: " + raw);
        }
    }

    private static String requiredString(Object raw, String field) {
        String value = raw != null ? raw.toString().trim() : "";
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value;
    }

    private static boolean parseEnabled(Object raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(raw.toString());
    }
}
