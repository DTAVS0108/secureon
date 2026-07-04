package com.secureone.auth.iga;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    List<AccessRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
