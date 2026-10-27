package com.secureone.auth.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_endpoint")
@Getter
@Setter
public class WebhookEndpoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "events", nullable = false, columnDefinition = "jsonb")
    private List<String> events = new ArrayList<>();

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** After a rotation, the old secret also signs deliveries until this time (receivers switch over). */
    @Column(name = "previous_secret_encrypted")
    private String previousSecretEncrypted;

    @Column(name = "previous_secret_expires_at")
    private Instant previousSecretExpiresAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
