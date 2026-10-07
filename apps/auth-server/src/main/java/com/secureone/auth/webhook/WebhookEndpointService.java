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

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, UUID id) {
        return toResponse(requireEndpoint(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDeliveries(UUID tenantId, UUID endpointId) {
        requireEndpoint(tenantId, endpointId);
        return deliveries.findByEndpointIdOrderByCreatedAtDesc(endpointId).stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    public Map<String, Object> create(UUID tenantId, Map<String, Object> body) {
        String url = targets.validateForRegistration(requiredString(body.get("url"), "url"));
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenantId(tenantId);
        endpoint.setApplicationId(parseUuid(body.get("applicationId")));
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
            endpoint.setApplicationId(parseUuid(body.get("applicationId")));
        }
        if (body.containsKey("events")) {
            endpoint.setEvents(parseEvents(body.get("events")));
        }
        if (body.containsKey("enabled")) {
            endpoint.setEnabled(parseEnabled(body.get("enabled"), endpoint.isEnabled()));
        }
        return toResponse(endpoints.save(endpoint));
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
