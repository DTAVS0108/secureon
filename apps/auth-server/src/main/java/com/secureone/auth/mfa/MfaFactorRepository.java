package com.secureone.auth.mfa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MfaFactorRepository extends JpaRepository<MfaFactor, UUID> {
    List<MfaFactor> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /** Bulk delete: a no-op instead of a StaleStateException (500) when a concurrent request deleted the rows. */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM MfaFactor x WHERE x.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    Optional<MfaFactor> findByIdAndUserId(UUID id, UUID userId);

    Optional<MfaFactor> findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(UUID userId, String type);

    List<MfaFactor> findByUserIdAndType(UUID userId, String type);

    @Query(
            value =
                    "SELECT * FROM mfa_factor WHERE type = 'passkey' AND metadata->>'credentialId' = :credentialId LIMIT 1",
            nativeQuery = true)
    Optional<MfaFactor> findPasskeyByCredentialId(@Param("credentialId") String credentialId);
}
