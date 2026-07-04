package com.secureone.auth.serviceaccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceAccountRepository extends JpaRepository<ServiceAccount, UUID> {

    List<ServiceAccount> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<ServiceAccount> findByTenantIdAndName(UUID tenantId, String name);
}
