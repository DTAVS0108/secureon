package com.secureone.auth.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A role given to a user, for good or until {@code expiresAt}. An expired assignment is never read (it gives
 * nothing from that moment) and is removed by {@link ExpiredRoleAssignmentCleanup}.
 */
@Entity
@Table(name = "user_role")
@SQLRestriction("(expires_at IS NULL OR expires_at > now())")
@Getter
@Setter
public class UserRole {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
}
