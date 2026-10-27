package com.secureone.auth.user;

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
@Table(name = "user_credential")
@Getter
@Setter
public class UserCredential {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "algorithm", nullable = false)
    private String algorithm = "bcrypt";

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null when the active policy had expiryDays = 0 at set time. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "expiry_warning_sent_at")
    private Instant expiryWarningSentAt;

    @Column(name = "expiry_expired_notice_sent_at")
    private Instant expiryExpiredNoticeSentAt;

    /** A temporary password from an administrator: the user must choose a new one at their next sign-in. */
    @Column(name = "must_change", nullable = false)
    private boolean mustChange;

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
