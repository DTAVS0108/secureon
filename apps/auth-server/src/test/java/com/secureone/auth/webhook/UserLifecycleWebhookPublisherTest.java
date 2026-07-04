package com.secureone.auth.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.secureone.auth.user.UserAccount;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class UserLifecycleWebhookPublisherTest {

    @Mock
    private WebhookEventPublisher publisher;

    @Mock
    private ObjectProvider<WebhookEventPublisher> provider;

    private UserLifecycleWebhookPublisher service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<WebhookEventPublisher> consumer = invocation.getArgument(0);
            consumer.accept(publisher);
            return null;
        }).when(provider).ifAvailable(any());
        service = new UserLifecycleWebhookPublisher(provider);
    }

    @Test
    void userCreatedPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setEmail("user@example.com");
        user.setStatus("ACTIVE");

        service.userCreated(user, applicationId, "scim");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq(tenantId), eq(applicationId), eq("user.created"), payload.capture());
    }
}
