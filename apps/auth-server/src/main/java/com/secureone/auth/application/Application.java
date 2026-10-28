package com.secureone.auth.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A relying-party application registered under a tenant. */
@Entity
@Table(name = "application")
@Getter
@Setter
public class Application {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    /** Dedicated PostgreSQL schema for app-isolated IAM tables; null = legacy shared platform tables. */
    @Column(name = "schema_name")
    private String schemaName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** A detached copy (config deep-copied), for values handed out from a cache. */
    public Application detachedCopy() {
        Application copy = new Application();
        copy.id = id;
        copy.tenantId = tenantId;
        copy.name = name;
        copy.slug = slug;
        copy.description = description;
        copy.status = status;
        copy.schemaName = schemaName;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) com.secureone.auth.platform.PlatformSettingsService.copy(this.config);
        copy.config = config != null ? config : new HashMap<>();
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
