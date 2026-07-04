package com.secureone.auth.scim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScimGroupRepository extends JpaRepository<ScimGroup, UUID> {

    List<ScimGroup> findByTenantIdOrderByDisplayNameAsc(UUID tenantId);

    Optional<ScimGroup> findByTenantIdAndExternalId(UUID tenantId, String externalId);
}
