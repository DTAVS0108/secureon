package com.secureone.auth.federation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderIdAndProviderSubject(UUID providerId, String providerSubject);

    Optional<UserIdentity> findByUserIdAndProviderId(UUID userId, UUID providerId);

    List<UserIdentity> findByUserId(UUID userId);
}
