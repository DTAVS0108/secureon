package com.secureone.auth.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<UserAccount> findByTenantIdAndPhone(UUID tenantId, String phone);

    /**
     * The account a session name's key belongs to ({@link UserAccount#getLoginKey()}): an email when it
     * has an @, otherwise a phone number.
     */
    default Optional<UserAccount> findByTenantIdAndLoginKey(UUID tenantId, String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String trimmed = key.trim();
        return trimmed.contains("@")
                ? findByTenantIdAndEmail(tenantId, trimmed.toLowerCase(java.util.Locale.ROOT))
                : findByTenantIdAndPhone(tenantId, trimmed);
    }

    long countByTenantId(UUID tenantId);

    List<UserAccount> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<UserAccount> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = "SELECT * FROM user_account WHERE attributes->>'webauthnUserHandle' = :handle",
            nativeQuery = true)
    List<UserAccount> findByWebAuthnUserHandle(@Param("handle") String handle);
}
