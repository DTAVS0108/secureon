package com.secureone.auth.account.otp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    @Query(
            """
            SELECT c FROM OtpChallenge c
            WHERE c.userId = :userId AND c.purpose = :purpose AND c.consumedAt IS NULL
            """)
    List<OtpChallenge> findActiveByUserAndPurpose(@Param("userId") UUID userId, @Param("purpose") String purpose);

    Optional<OtpChallenge> findByIdAndConsumedAtIsNull(UUID id);

    @Modifying
    @Query(
            """
            UPDATE OtpChallenge c SET c.consumedAt = CURRENT_TIMESTAMP
            WHERE c.userId = :userId AND c.purpose = :purpose AND c.consumedAt IS NULL
            """)
    void invalidateActive(@Param("userId") UUID userId, @Param("purpose") String purpose);
}
