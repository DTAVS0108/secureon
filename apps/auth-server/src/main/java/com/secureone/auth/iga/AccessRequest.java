package com.secureone.auth.iga;

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
@Table(name = "access_request")
@Getter
@Setter
public class AccessRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "grant_type")
    private String grantType;

    @Column(name = "granted_role_id")
    private UUID grantedRoleId;

    @Column(name = "granted_user_id")
    private UUID grantedUserId;

    @Column(name = "granted_role_name")
    private String grantedRoleName;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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
