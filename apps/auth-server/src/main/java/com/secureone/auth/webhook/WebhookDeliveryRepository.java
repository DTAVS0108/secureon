package com.secureone.auth.webhook;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId);

    /** The latest deliveries shown in the console. */
    List<WebhookDelivery> findTop100ByEndpointIdOrderByCreatedAtDesc(UUID endpointId);
}
