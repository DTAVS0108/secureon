package com.secureone.auth.webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEventPublisher {

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryService deliveryService;

    public WebhookEventPublisher(WebhookEndpointRepository endpoints, WebhookDeliveryService deliveryService) {
        this.endpoints = endpoints;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public void publish(UUID tenantId, UUID applicationId, String eventType, Map<String, Object> payload) {
        if (tenantId == null || eventType == null || eventType.isBlank()) {
            return;
        }
        List<WebhookEndpoint> matching = endpoints.findMatching(tenantId, applicationId);
        Map<String, Object> eventPayload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
        for (WebhookEndpoint endpoint : matching) {
            if (!endpoint.isEnabled()) {
                continue;
            }
            if (!subscribesTo(endpoint.getEvents(), eventType)) {
                continue;
            }
            // Stored now, sent after this transaction commits (and retried until the receiver accepts it).
            deliveryService.enqueue(endpoint.getId(), eventType, eventPayload);
        }
    }

    private static boolean subscribesTo(List<String> events, String eventType) {
        if (events == null || events.isEmpty()) {
            return true;
        }
        for (String subscribed : events) {
            if (subscribed == null || subscribed.isBlank()) {
                continue;
            }
            if ("*".equals(subscribed) || eventType.equals(subscribed)) {
                return true;
            }
        }
        return false;
    }
}
