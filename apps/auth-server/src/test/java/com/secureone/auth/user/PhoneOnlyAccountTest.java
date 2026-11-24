package com.secureone.auth.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.SignupPolicy;
import com.secureone.auth.platform.VerificationPolicyDefaults;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Accounts created with a phone number only: what they sign in as, and how that is found again. */
class PhoneOnlyAccountTest {

    @Test
    void signsInAsEmailWhenThereIsOneOtherwiseAsPhone() {
        UserAccount both = new UserAccount();
        both.setEmail("ann@example.com");
        both.setPhone("+15550100");
        assertEquals("ann@example.com", both.getLoginKey());
        assertTrue(both.hasEmail());

        UserAccount phoneOnly = new UserAccount();
        phoneOnly.setPhone("+15550100");
        assertEquals("+15550100", phoneOnly.getLoginKey());
        assertFalse(phoneOnly.hasEmail());
    }

    @Test
    void sessionNamesOfUsersAreRecognisedButClientsAndOperatorsAreNot() {
        assertTrue(UserAccount.isUserSessionName("shop:ann@example.com"));
        assertTrue(UserAccount.isUserSessionName("shop:+15550100"));
        assertFalse(UserAccount.isUserSessionName("shop-web-client"));
        assertFalse(UserAccount.isUserSessionName("shop:"));
        assertFalse(UserAccount.isUserSessionName(":ann@example.com"));
        assertFalse(UserAccount.isUserSessionName("shop:client-id"));
        assertFalse(UserAccount.isUserSessionName(null));
    }

    @Test
    void loginKeyLookupUsesEmailForAddressesAndPhoneOtherwise() {
        UserAccountRepository repo = mock(UserAccountRepository.class, Mockito.CALLS_REAL_METHODS);
        UUID tenant = UUID.randomUUID();
        UserAccount user = new UserAccount();
        Mockito.doReturn(Optional.of(user)).when(repo).findByTenantIdAndEmail(tenant, "ann@example.com");
        Mockito.doReturn(Optional.of(user)).when(repo).findByTenantIdAndPhone(tenant, "+15550100");

        assertTrue(repo.findByTenantIdAndLoginKey(tenant, " Ann@Example.com ").isPresent());
        assertTrue(repo.findByTenantIdAndLoginKey(tenant, "+15550100").isPresent());
        assertTrue(repo.findByTenantIdAndLoginKey(tenant, "").isEmpty());
        assertTrue(repo.findByTenantIdAndLoginKey(tenant, null).isEmpty());
        verify(repo, never()).findByTenantIdAndPhone(any(), Mockito.eq("ann@example.com"));
    }

    @Test
    void phoneSignupIsOffByDefaultAndKeptWhenSaved() {
        @SuppressWarnings("unchecked")
        Map<String, Object> signup = (Map<String, Object>) VerificationPolicyDefaults.platformDefaults().get("signup");
        assertEquals(false, signup.get("phoneSignup"));

        @SuppressWarnings("unchecked")
        Map<String, Object> saved = (Map<String, Object>) VerificationPolicyDefaults
                .sanitizeForSave(Map.of("signup", Map.of("phoneSignup", true)))
                .get("signup");
        assertEquals(true, saved.get("phoneSignup"));
        assertNotNull(saved.get("otpLength"));
    }

    @Test
    void domainLimitedSignupRefusesAnAccountWithoutEmail() {
        assertNull(SignupPolicy.NONE.domainRefusal(null));
        SignupPolicy limited = SignupPolicy.sanitize(Map.of("allowedDomains", List.of("example.com")));
        assertNotNull(limited.domainRefusal(null));
        SignupPolicy blocked = SignupPolicy.sanitize(Map.of("blockedDomains", List.of("spam.test")));
        assertNull(blocked.domainRefusal(null));
    }
}
