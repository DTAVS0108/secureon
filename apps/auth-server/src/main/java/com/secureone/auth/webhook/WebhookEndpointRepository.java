package com.secureone.auth.webhook;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("""
            SELECT e FROM WebhookEndpoint e
            WHERE e.tenantId = :tenantId
              AND e.enabled = true
              AND (:applicationId IS NULL OR e.applicationId IS NULL OR e.applicationId = :applicationId)
            """)
    List<WebhookEndpoint> findMatching(
            @Param("tenantId") UUID tenantId, @Param("applicationId") UUID applicationId);
}
