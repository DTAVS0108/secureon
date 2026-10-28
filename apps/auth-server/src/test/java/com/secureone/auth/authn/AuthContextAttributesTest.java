package com.secureone.auth.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class AuthContextAttributesTest {

    private final UserDetails user = User.withUsername("tenant-a:member@example.com").password("").roles("USER").build();

    private MockHttpServletRequest signIn(String... factors) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthContextAttributes.recordSignIn(request, SignedInAuthentication.of(user, factors));
        return request;
    }

    @Test
    void passwordAloneIsOneStep() {
        MockHttpServletRequest request = signIn(FactorGrantedAuthority.PASSWORD_AUTHORITY);

        assertEquals(List.of("pwd"), AuthContextAttributes.amr(request));
        assertEquals("1", AuthContextAttributes.acr(request));
    }

    @Test
    void passwordThenCodeIsTwoSteps() {
        // Before the fix the session ended up at acr 1 after the MFA step.
        MockHttpServletRequest request =
                signIn(FactorGrantedAuthority.PASSWORD_AUTHORITY, SignedInAuthentication.MFA_FACTOR);

        assertEquals(List.of("pwd", "otp", "mfa"), AuthContextAttributes.amr(request));
        assertEquals("2", AuthContextAttributes.acr(request));
    }

    @Test
    void magicLinkThenCodeDoesNotClaimAPassword() {
        MockHttpServletRequest request =
                signIn(FactorGrantedAuthority.OTT_AUTHORITY, SignedInAuthentication.MFA_FACTOR);

        assertEquals(List.of("otp", "mfa"), AuthContextAttributes.amr(request));
        assertEquals("2", AuthContextAttributes.acr(request));
    }

    @Test
    void passkeyIsTwoStepsWithStandardValues() {
        MockHttpServletRequest request = signIn(FactorGrantedAuthority.WEBAUTHN_AUTHORITY);

        assertEquals(List.of("hwk", "user", "passkey", "mfa"), AuthContextAttributes.amr(request));
        assertEquals("2", AuthContextAttributes.acr(request));
    }

    @Test
    void externalProviderAloneIsOneStep() {
        MockHttpServletRequest request = signIn(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY);

        assertEquals(List.of("fed"), AuthContextAttributes.amr(request));
        assertEquals("1", AuthContextAttributes.acr(request));
    }

    @Test
    void aNewSignInReplacesWhatAnEarlierOneLeftInTheSession() {
        MockHttpServletRequest request =
                signIn(FactorGrantedAuthority.PASSWORD_AUTHORITY, SignedInAuthentication.MFA_FACTOR);

        AuthContextAttributes.recordSignIn(
                request, SignedInAuthentication.of(user, FactorGrantedAuthority.PASSWORD_AUTHORITY));

        assertEquals(List.of("pwd"), AuthContextAttributes.amr(request));
        assertEquals("1", AuthContextAttributes.acr(request));
    }
}
