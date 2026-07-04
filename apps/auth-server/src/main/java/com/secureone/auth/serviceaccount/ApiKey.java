package com.secureone.auth.serviceaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "api_key")
@Getter
@Setter
public class ApiKey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "service_account_id", nullable = false)
    private UUID serviceAccountId;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "label")
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isActive() {
        if (revokedAt != null) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
