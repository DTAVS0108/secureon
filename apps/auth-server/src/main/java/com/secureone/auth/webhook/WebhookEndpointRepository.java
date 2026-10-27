package com.secureone.auth.webhook;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<WebhookEndpoint> findByTenantIdAndApplicationIdOrderByCreatedAtDesc(UUID tenantId, UUID applicationId);

    /**
     * The endpoints for an event: the tenant's (all its applications) and, for an event of one application, that
     * application's own. An application's endpoint never receives another application's or tenant-wide events.
     */
    @Query("""
            SELECT e FROM WebhookEndpoint e
            WHERE e.tenantId = :tenantId
              AND e.enabled = true
              AND (e.applicationId IS NULL OR e.applicationId = :applicationId)
            """)
    List<WebhookEndpoint> findMatching(
            @Param("tenantId") UUID tenantId, @Param("applicationId") UUID applicationId);
}
