package com.secureone.auth.serviceaccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByServiceAccountIdOrderByCreatedAtDesc(UUID serviceAccountId);

    Optional<ApiKey> findByKeyHash(String keyHash);
}
