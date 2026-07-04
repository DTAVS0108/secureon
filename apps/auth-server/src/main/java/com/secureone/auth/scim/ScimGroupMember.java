package com.secureone.auth.scim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "scim_group_member")
@IdClass(ScimGroupMember.ScimGroupMemberId.class)
@Getter
@Setter
public class ScimGroupMember {

    @Id
    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public record ScimGroupMemberId(UUID groupId, UUID userId) implements Serializable {}
}
