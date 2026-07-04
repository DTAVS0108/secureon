package com.secureone.auth.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Organization> findByTenantIdAndSlug(UUID tenantId, String slug);
}
