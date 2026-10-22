package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.twostep.TwoStepMethod;
import com.secureone.auth.twostep.TwoStepPolicyResolver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Typed reads of the effective policy. The sign-in decision is tested in TwoStepServiceTest. */
class MfaPolicyServiceTest {

    private final TwoStepPolicyResolver resolver = mock(TwoStepPolicyResolver.class);
    private final MfaPolicyService policy = new MfaPolicyService(resolver, mock(MfaFactorRepository.class));

    private void effective(Map<String, Object> overrides) {
        Map<String, Object> p = MfaPolicyDefaults.sanitizeForSave(overrides);
        when(resolver.resolve(any())).thenReturn(new TwoStepPolicyResolver.Resolved(p, null, Map.of()));
    }

    @Test
    void readsModeMethodsAndStrength() {
        effective(Map.of("mode", "required", "methods", List.of("totp", "sms"), "minimumStrength", "phishing_resistant"));
        assertEquals("required", policy.loginMfaRequired(UUID.randomUUID()));
        assertEquals(List.of(TwoStepMethod.TOTP, TwoStepMethod.SMS), policy.allowedMethods(null));
        assertEquals(TwoStepMethod.Strength.PHISHING_RESISTANT, policy.minimumStrength(null));
    }

    @Test
    void totpAndPasskeyAvailabilityFollowTheAllowedMethods() {
        effective(Map.of("methods", List.of("security_key")));
        assertFalse(policy.totpAvailable(null));
        assertTrue(policy.passkeysAvailable(null));
    }

    @Test
    void numbersComeFromThePolicy() {
        effective(Map.of("rememberDeviceDays", 30, "challengeTtlSeconds", 120, "freshStepMinutes", 10));
        assertEquals(30, policy.rememberDeviceDays(null));
        assertEquals(120, policy.challengeTtlSeconds(null));
        assertEquals(10, policy.freshStepMinutes(null));
    }
}
