package com.secureone.auth.notify;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHashAndTypeAndConsumedAtIsNull(String tokenHash, EmailTokenType type);

    // Flush first: clearing the persistence context would otherwise drop the caller's unsaved changes (an audit
    // entry, a webhook delivery) made earlier in the same transaction.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EmailToken t WHERE t.userId = :userId AND t.type = :type AND t.consumedAt IS NULL")
    void deleteActiveByUserIdAndType(@Param("userId") UUID userId, @Param("type") EmailTokenType type);
}
