package com.secureone.auth.federation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityProviderRepository extends JpaRepository<IdentityProvider, UUID> {

    List<IdentityProvider> findByTenantIdAndApplicationIdIsNullOrderByAliasAsc(UUID tenantId);

    List<IdentityProvider> findByTenantIdAndApplicationIdOrderByAliasAsc(UUID tenantId, UUID applicationId);

    Optional<IdentityProvider> findByTenantIdAndApplicationIdIsNullAndAliasIgnoreCase(UUID tenantId, String alias);

    Optional<IdentityProvider> findByTenantIdAndApplicationIdAndAliasIgnoreCase(
            UUID tenantId, UUID applicationId, String alias);
}
