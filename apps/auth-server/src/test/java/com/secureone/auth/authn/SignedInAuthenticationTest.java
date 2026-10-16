package com.secureone.auth.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class SignedInAuthenticationTest {

    private final UserDetails user =
            User.withUsername("acme:a@example.com").password("x").roles("USER").build();

    @Test
    void carriesTheCompletingFactorStampedNow() {
        Instant before = Instant.now();

        Authentication auth = SignedInAuthentication.of(
                user, FactorGrantedAuthority.PASSWORD_AUTHORITY, SignedInAuthentication.MFA_FACTOR);

        assertTrue(auth.isAuthenticated());
        assertEquals(user, auth.getPrincipal());
        List<FactorGrantedAuthority> factors = auth.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .map(FactorGrantedAuthority.class::cast)
                .toList();
        assertEquals(
                List.of(FactorGrantedAuthority.PASSWORD_AUTHORITY, SignedInAuthentication.MFA_FACTOR),
                factors.stream().map(GrantedAuthority::getAuthority).toList());
        // The ID token's auth_time comes from these; without one the token endpoint fails.
        for (FactorGrantedAuthority factor : factors) {
            assertNotNull(factor.getIssuedAt());
            assertTrue(!factor.getIssuedAt().isBefore(before));
        }
    }

    @Test
    void keepsTheUsersOwnAuthorities() {
        Authentication auth = SignedInAuthentication.of(user, FactorGrantedAuthority.OTT_AUTHORITY);

        assertTrue(auth.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
    }
}
