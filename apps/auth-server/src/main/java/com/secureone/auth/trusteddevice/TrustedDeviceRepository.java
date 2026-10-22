package com.secureone.auth.trusteddevice;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    Optional<TrustedDevice> findByUserIdAndDeviceTokenHash(UUID userId, String deviceTokenHash);

    java.util.List<TrustedDevice> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TrustedDevice> findByIdAndUserId(UUID id, UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    long deleteByUserId(UUID userId);
}
