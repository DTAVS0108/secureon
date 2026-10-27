package com.secureone.auth.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_delivery")
@Getter
@Setter
public class WebhookDelivery {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Tries so far (a failed try is retried with backoff until {@link WebhookDeliveryService#MAX_ATTEMPTS}). */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (nextAttemptAt == null && "PENDING".equals(status)) {
            nextAttemptAt = createdAt;
        }
    }
}
