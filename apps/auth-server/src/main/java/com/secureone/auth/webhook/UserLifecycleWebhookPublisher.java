package com.secureone.auth.webhook;

import com.secureone.auth.user.UserAccount;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class UserLifecycleWebhookPublisher {

    private final ObjectProvider<WebhookEventPublisher> webhookPublisher;

    public UserLifecycleWebhookPublisher(ObjectProvider<WebhookEventPublisher> webhookPublisher) {
        this.webhookPublisher = webhookPublisher;
    }

    public void userCreated(UserAccount user, UUID applicationId, String source) {
        webhookPublisher.ifAvailable(publisher -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", user.getId().toString());
            payload.put("email", user.getEmail());
            payload.put("tenantId", user.getTenantId().toString());
            payload.put("status", user.getStatus());
            if (source != null && !source.isBlank()) {
                payload.put("source", source);
            }
            if (applicationId != null) {
                payload.put("applicationId", applicationId.toString());
            }
            publisher.publish(user.getTenantId(), applicationId, "user.created", payload);
        });
    }
}
