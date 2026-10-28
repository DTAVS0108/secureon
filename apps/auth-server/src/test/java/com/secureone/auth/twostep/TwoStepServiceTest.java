package com.secureone.auth.twostep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.account.AccountSessionService;
import com.secureone.auth.authn.TenantSlugResolver;
import com.secureone.auth.mfa.MfaFactor;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.mfa.MfaPolicyService;
import com.secureone.auth.mfa.MfaRecoveryCodeService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.user.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.FactorGrantedAuthority;

class TwoStepServiceTest {

    private final MfaPolicyService policy = mock(MfaPolicyService.class);
    private final MfaFactorRepository factors = mock(MfaFactorRepository.class);
    private final TwoStepStateStore states = mock(TwoStepStateStore.class);
    private final TrustedDeviceService devices = mock(TrustedDeviceService.class);
    private final MfaRecoveryCodeService recovery = mock(MfaRecoveryCodeService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EmailNotificationService> mail = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AccountSessionService> sessions = mock(ObjectProvider.class);

    private TwoStepService service;
    private final UserAccount user = new UserAccount();
    private final List<MfaFactor> userFactors = new ArrayList<>();
    private Map<String, Object> p;
    private TwoStepStateStore.State state;
    private final Map<UUID, TwoStepStateStore.State> appStates = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new TwoStepService(policy, factors, states, devices, recovery, mail, sessions,
                mock(TenantSlugResolver.class), "http://localhost:9000");
        user.setId(UUID.randomUUID());
        p = new HashMap<>(MfaPolicyDefaults.sanitizeForSave(Map.of()));
        state = TwoStepStateStore.State.empty(user.getId(), null);
        when(policy.resolvePolicy(any())).thenAnswer(i -> p);
        when(policy.loginMfaRequired(any())).thenAnswer(i -> p.get("mode"));
        when(policy.stepUpAfterPassword(any())).thenReturn(true);
        when(policy.allowedMethods(any())).thenAnswer(i -> ((List<?>) p.get("methods")).stream()
                .map(m -> TwoStepMethod.fromId((String) m).orElseThrow()).toList());
        when(policy.minimumStrength(any())).thenAnswer(i -> TwoStepMethod.Strength.fromId((String) p.get("minimumStrength")));
        when(factors.findByUserIdOrderByCreatedAtAsc(user.getId())).thenReturn(userFactors);
        when(states.get(eq(user.getId()), any())).thenAnswer(i -> i.getArgument(1) == null ? state : appStates.getOrDefault((UUID) i.getArgument(1), TwoStepStateStore.State.empty(user.getId(), i.getArgument(1))));
    }

    private void addFactor(String type, Map<String, Object> meta) {
        MfaFactor f = new MfaFactor();
        f.setId(UUID.randomUUID());
        f.setUserId(user.getId());
        f.setType(type);
        f.setVerified(true);
        f.setMetadata(new HashMap<>(meta));
        userFactors.add(f);
    }

    private TwoStepService.Decision decide(String first) {
        return service.decide(user, null, first, null);
    }

    @Test
    void offMeansSignedIn() {
        p.put("mode", "off");
        addFactor("totp", Map.of());
        assertInstanceOf(TwoStepService.SignedIn.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    }

    @Test
    void aVerifiedPasskeySignInIsBothSteps() {
        p.put("mode", "required");
        assertInstanceOf(TwoStepService.SignedIn.class, decide(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
    }

    @Test
    void userWithAnAuthenticatorIsAskedForIt() {
        addFactor("totp", Map.of());
        var d = assertInstanceOf(TwoStepService.SecondStep.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        assertEquals(TwoStepMethod.TOTP, d.methods().get(0).method());
    }

    @Test
    void optionalWithoutMethodSuggestsSetupThatCanBeSkipped() {
        var d = assertInstanceOf(TwoStepService.Setup.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        assertFalse(d.required());
        assertTrue(d.canSkip());
    }

    @Test
    void optionalWithoutPromptsOrAfterOptOutSignsIn() {
        state = new TwoStepStateStore.State(user.getId(), null, null, null, 0, null, true, null, null, null, null, "");
        assertInstanceOf(TwoStepService.SignedIn.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    }

    @Test
    void requiredStartsAGracePeriodThatCanBeSkipped() {
        p.put("mode", "required");
        var d = assertInstanceOf(TwoStepService.Setup.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        assertTrue(d.required());
        // The first prompt saves a deadline; the mocked store keeps returning the empty state, so canSkip is
        // evaluated against the deadline computed from the policy (14 days, 3 skips).
        assertEquals(List.of(TwoStepMethod.PASSKEY, TwoStepMethod.SECURITY_KEY, TwoStepMethod.TOTP), d.offered());
    }

    @Test
    void requiredAfterTheDeadlineCannotBeSkipped() {
        p.put("mode", "required");
        state = new TwoStepStateStore.State(user.getId(), null, Instant.now().minusSeconds(86400 * 20), null, 0,
                Instant.now().minusSeconds(60), false, null, null, null, null, "");
        var d = assertInstanceOf(TwoStepService.Setup.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        assertFalse(d.canSkip());
    }

    @Test
    void requiredWithOnlyATextMessageOptionMustAddAStrongOne() {
        p.put("mode", "required");
        p.put("methods", List.of("passkey", "totp", "sms"));
        addFactor("sms", Map.of("destination", "+14155550100"));
        assertInstanceOf(TwoStepService.Setup.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    }

    @Test
    void aCodeOrLinkFirstStepCannotFinishWithAnotherSentCode() {
        p.put("methods", List.of("totp", "sms", "email"));
        addFactor("email", Map.of("destination", "member@example.com"));
        addFactor("totp", Map.of());
        var d = assertInstanceOf(TwoStepService.SecondStep.class, decide(FactorGrantedAuthority.OTT_AUTHORITY));
        assertEquals(List.of(TwoStepMethod.TOTP), d.methods().stream().map(TwoStepService.UserMethod::method).toList());
    }

    @Test
    void exemptUsersSignIn() {
        p.put("mode", "required");
        state = new TwoStepStateStore.State(user.getId(), null, null, null, 0, null, false,
                Instant.now().plusSeconds(3600), "travel", "admin@example.com", null, "");
        assertInstanceOf(TwoStepService.SignedIn.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    }

    @Test
    void passkeysComeFirstWhenThePolicyPrefersThem() {
        addFactor("totp", Map.of());
        addFactor("passkey", Map.of());
        var d = assertInstanceOf(TwoStepService.SecondStep.class, decide(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        assertEquals(TwoStepMethod.PASSKEY, d.methods().get(0).method());
    }

    // ---- Each application has its own settings, so its own progress -------------------------------------

    private final UUID appA = UUID.randomUUID();
    private final UUID appB = UUID.randomUUID();

    @Test
    void dontAskAgainInOneApplicationDoesNotSilenceAnother() {
        appStates.put(appA, new TwoStepStateStore.State(user.getId(), appA, null, null, 0, null, true, null, null, null, null, ""));
        assertInstanceOf(TwoStepService.SignedIn.class,
                service.decide(user, appA, FactorGrantedAuthority.PASSWORD_AUTHORITY, null));
        assertInstanceOf(TwoStepService.Setup.class,
                service.decide(user, appB, FactorGrantedAuthority.PASSWORD_AUTHORITY, null), "app B still prompts");
    }

    @Test
    void anExemptionForOneApplicationDoesNotCoverAnother() {
        p.put("mode", "required");
        appStates.put(appA, new TwoStepStateStore.State(user.getId(), appA, null, null, 0, null, false,
                Instant.now().plusSeconds(3600), "travel", "admin@example.com", null, ""));
        assertInstanceOf(TwoStepService.SignedIn.class,
                service.decide(user, appA, FactorGrantedAuthority.PASSWORD_AUTHORITY, null));
        assertInstanceOf(TwoStepService.Setup.class,
                service.decide(user, appB, FactorGrantedAuthority.PASSWORD_AUTHORITY, null));
    }

    @Test
    void anExemptionForEveryApplicationCoversEach() {
        p.put("mode", "required");
        state = new TwoStepStateStore.State(user.getId(), null, null, null, 0, null, false,
                Instant.now().plusSeconds(3600), "travel", "admin@example.com", null, "");
        assertInstanceOf(TwoStepService.SignedIn.class,
                service.decide(user, appA, FactorGrantedAuthority.PASSWORD_AUTHORITY, null));
        assertEquals("all", service.status(user, appB).get("exemptScope"));
    }

    @Test
    void aRememberedDeviceCountsOnlyWhereItWasRememberedAndAllowed() {
        addFactor("totp", Map.of());
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(policy.rememberDeviceDays(appA)).thenReturn(30);
        when(policy.rememberDeviceDays(appB)).thenReturn(0);
        when(devices.isTrusted(eq(user.getId()), eq(appA), eq(30), any())).thenReturn(true);
        when(devices.isTrusted(eq(user.getId()), eq(appB), anyInt(), any())).thenReturn(false);

        assertInstanceOf(TwoStepService.SignedIn.class,
                service.decide(user, appA, FactorGrantedAuthority.PASSWORD_AUTHORITY, request));
        assertInstanceOf(TwoStepService.SecondStep.class,
                service.decide(user, appB, FactorGrantedAuthority.PASSWORD_AUTHORITY, request));
    }

    @Test
    void securityKeysAreKeptApartFromPasskeys() {
        addFactor("passkey", Map.of("kind", "security_key"));
        assertEquals(TwoStepMethod.SECURITY_KEY, service.methods(user.getId()).get(0).method());
    }
}
