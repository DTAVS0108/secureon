package com.secureone.auth.webhook;

import com.secureone.auth.audit.AuditLog;
import com.secureone.auth.audit.AuditRecordedEvent;
import com.secureone.auth.tenant.RlsScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends a tenant webhook for each audit entry that is a catalogued event ({@link WebhookEvents}). */
@Component
public class AuditWebhookBridge {

    private static final Logger log = LoggerFactory.getLogger(AuditWebhookBridge.class);

    private final WebhookEventPublisher publisher;

    public AuditWebhookBridge(WebhookEventPublisher publisher) {
        this.publisher = publisher;
    }

    @org.springframework.scheduling.annotation.Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAudit(AuditRecordedEvent event) {
        AuditLog entry = event.log();
        String type = WebhookEvents.FROM_AUDIT.get(entry.getAction());
        if (type == null || entry.getTenantId() == null) {
            return;
        }
        Map<String, Object> meta = entry.getMetadata() != null ? entry.getMetadata() : Map.of();
        if ("failure".equals(meta.get("result"))) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auditId", entry.getId() != null ? entry.getId().toString() : null);
        payload.put("action", entry.getAction());
        payload.put("targetType", entry.getTargetType());
        payload.put("targetId", entry.getTargetId() != null ? entry.getTargetId().toString() : null);
        payload.put("target", meta.get("targetLabel"));
        payload.put("actor", meta.get("actorEmail"));
        payload.put("applicationId", entry.getApplicationId() != null ? entry.getApplicationId().toString() : null);
        payload.put("occurredAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : java.time.Instant.now().toString());
        try {
            RlsScope.runAsPlatform(() -> publisher.publish(entry.getTenantId(), entry.getApplicationId(), type, payload));
        } catch (RuntimeException e) {
            // Webhooks never break the action that caused them.
            log.warn("Webhook {} for audit {} not queued: {}", type, entry.getAction(), e.getMessage());
        }
    }
}
