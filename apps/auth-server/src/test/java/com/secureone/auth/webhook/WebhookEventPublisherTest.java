package com.secureone.auth.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookEventPublisherTest {

    @Mock
    private WebhookEndpointRepository endpoints;

    @Mock
    private WebhookDeliveryService deliveryService;

    private WebhookEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebhookEventPublisher(endpoints, deliveryService);
    }

    @Test
    void publishEnqueuesDeliveryForMatchingEnabledEndpoint() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setEnabled(true);
        endpoint.setEvents(List.of("user.login.succeeded"));

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(UUID.randomUUID());

        when(endpoints.findMatching(tenantId, applicationId)).thenReturn(List.of(endpoint));
        when(deliveryService.enqueue(eq(endpoint.getId()), eq("user.login.succeeded"), any()))
                .thenReturn(delivery);

        publisher.publish(tenantId, applicationId, "user.login.succeeded", Map.of("userId", "abc"));

        verify(deliveryService).enqueue(eq(endpoint.getId()), eq("user.login.succeeded"), any());
        verify(deliveryService).deliverAsync(endpoint, delivery);
    }

    @Test
    void publishSkipsDisabledEndpoints() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setEnabled(false);
        endpoint.setEvents(List.of("*"));

        when(endpoints.findMatching(tenantId, null)).thenReturn(List.of(endpoint));

        publisher.publish(tenantId, null, "user.login.succeeded", Map.of());

        verify(deliveryService, never()).enqueue(any(), any(), any());
    }

    @Test
    void publishSkipsWhenEventNotSubscribed() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setEnabled(true);
        endpoint.setEvents(List.of("user.created"));

        when(endpoints.findMatching(tenantId, null)).thenReturn(List.of(endpoint));

        publisher.publish(tenantId, null, "user.login.succeeded", Map.of());

        verify(deliveryService, never()).enqueue(any(), any(), any());
    }
}
