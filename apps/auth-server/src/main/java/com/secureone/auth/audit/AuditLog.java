package com.secureone.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
