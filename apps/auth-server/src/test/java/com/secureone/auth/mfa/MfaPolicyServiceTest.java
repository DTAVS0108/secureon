package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.user.UserAccount;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaPolicyServiceTest {

    @Mock
    private AuthSettingsService authSettings;

    @Mock
    private VerificationPolicyService verificationPolicy;

    @Mock
    private PlatformSettingsService platformSettings;

    @Mock
    private ApplicationSettingsService applicationSettings;

    @Mock
    private MfaFactorRepository mfaFactors;

    @Mock
    private com.secureone.auth.trusteddevice.TrustedDeviceService trustedDevices;

    @Mock
    private com.secureone.auth.application.ApplicationEffectiveSettingsService effective;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings;

    @InjectMocks
    private MfaPolicyService policy;

    @Test
    void requiresStepUpWhenTotpEnrolledAndPolicyOptional() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);

        when(effectiveSettings.getObject()).thenReturn(effective);
        when(effective.isMethodAvailable(null, "m_totp")).thenReturn(true);
        when(platformSettings.get("mfa_policy")).thenReturn(Map.of("stepUpAfterPassword", true));
        when(verificationPolicy.loginPolicy(null)).thenReturn(Map.of("mfaRequired", "optional"));
        when(mfaFactors.findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(userId, "totp"))
                .thenReturn(Optional.of(new MfaFactor()));

        assertTrue(policy.requiresMfaStepUp(user, null));
    }

    @Test
    void requiresStepUpWhenPasskeyRequiredAndEnrolled() {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);

        when(effectiveSettings.getObject()).thenReturn(effective);
        when(effective.isMethodAvailable(applicationId, "m_passkey")).thenReturn(true);
        when(applicationSettings.resolveMfaPolicy(applicationId))
                .thenReturn(Map.of("stepUpAfterPassword", true, "requirePasskeyWhenEnrolled", true));
        when(verificationPolicy.loginPolicy(applicationId)).thenReturn(Map.of("mfaRequired", "optional"));
        when(mfaFactors.findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(userId, "passkey"))
                .thenReturn(Optional.of(new MfaFactor()));

        assertTrue(policy.requiresMfaStepUp(user, applicationId));
    }

    @Test
    void skipsStepUpWhenMfaDisabled() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        when(effectiveSettings.getObject()).thenReturn(effective);
        assertFalse(policy.requiresMfaStepUp(user, null));
    }
}
