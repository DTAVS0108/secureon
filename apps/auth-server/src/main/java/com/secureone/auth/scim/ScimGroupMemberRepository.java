package com.secureone.auth.scim;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScimGroupMemberRepository extends JpaRepository<ScimGroupMember, ScimGroupMember.ScimGroupMemberId> {

    List<ScimGroupMember> findByGroupIdOrderByCreatedAtAsc(UUID groupId);

    void deleteByGroupId(UUID groupId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
}
