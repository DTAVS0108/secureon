package com.secureone.auth.mfa.passkey;

import com.secureone.auth.application.ApplicationDisplayName;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasskeyRegistrationService {

    private final RelyingParty relyingParty;
    private final WebAuthnCredentialRepository credentials;
    private final WebAuthnChallengeStore challenges;
    private final AuthSettingsService authSettings;
    private final org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings;

    private final ApplicationRepository applications;
    private final ApplicationDisplayName displayNames;
    private final com.secureone.auth.mfa.MfaPolicyService twoStepPolicy;

    public PasskeyRegistrationService(
            RelyingParty relyingParty,
            WebAuthnCredentialRepository credentials,
            WebAuthnChallengeStore challenges,
            AuthSettingsService authSettings,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings,
            ApplicationRepository applications,
            ApplicationDisplayName displayNames,
            com.secureone.auth.mfa.MfaPolicyService twoStepPolicy) {
        this.twoStepPolicy = twoStepPolicy;
        this.applications = applications;
        this.displayNames = displayNames;
        this.relyingParty = relyingParty;
        this.credentials = credentials;
        this.challenges = challenges;
        this.authSettings = authSettings;
        this.effectiveSettings = effectiveSettings;
    }

    public Map<String, Object> beginRegistration(UserAccount user, String tenantSlug, String label) {
        return beginRegistration(user, tenantSlug, label, null);
    }

    /**
     * What the browser shows: the app's name as the site (the RP id, which binds the credential, stays
     * the auth-server's domain) and the user's email as the account, not the internal
     * "tenant:email" username (which stays the key for credential lookups).
     */
    public Map<String, Object> beginRegistration(UserAccount user, String tenantSlug, String label, UUID applicationId) {
        return beginRegistration(user, tenantSlug, label, applicationId, null);
    }

    /**
     * {@code kind} "security_key" asks for a removable key, "passkey" for this phone or computer, null for either.
     * Allowed when the app offers passkeys for sign-in or as a two-step option.
     */
    public Map<String, Object> beginRegistration(
            UserAccount user, String tenantSlug, String label, UUID applicationId, String kind) {
        assertPasskeysAvailable(applicationId);
        String username = username(tenantSlug, user.getEmail());
        ByteArray userHandle = credentials.ensureUserHandle(user);
        PublicKeyCredentialCreationOptions started = WebAuthnErrors.uncheck(() -> relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(user.getEmail())
                                .displayName(displayName(user))
                                .id(userHandle)
                                .build())
                        // The device must check a PIN or biometric, so the passkey is two steps on its own.
                        .authenticatorSelection(selection(kind))
                        .build()));
        PublicKeyCredentialCreationOptions options = started.toBuilder()
                .rp(RelyingPartyIdentity.builder()
                        .id(relyingParty.getIdentity().getId())
                        .name(promptName(user, applicationId))
                        .build())
                .build();
        String requestId = challenges.saveRegistration(new WebAuthnChallengeStore.RegistrationPending(
                user.getId(), username, WebAuthnErrors.uncheck(options::toJson),
                "security_key".equals(kind) ? "security_key" : "passkey"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", requestId);
        out.put("publicKey", WebAuthnErrors.uncheck(options::toCredentialsCreateJson));
        return out;
    }

    /** The app's name, only for an app of the user's own organization. */
    private String promptName(UserAccount user, UUID applicationId) {
        if (applicationId == null) {
            return ApplicationDisplayName.PLATFORM_NAME;
        }
        return applications.findById(applicationId)
                .filter(app -> app.getTenantId().equals(user.getTenantId()))
                .map(displayNames::of)
                .orElse(ApplicationDisplayName.PLATFORM_NAME);
    }

    public Map<String, Object> finishRegistration(
            UserAccount user, UUID requestId, String credentialJson, String label) {
        WebAuthnChallengeStore.RegistrationPending pending = challenges
                .consumeRegistration(requestId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Passkey registration expired or invalid."));
        if (!user.getId().equals(pending.userId())) {
            throw new IllegalArgumentException("Passkey registration does not belong to this user.");
        }
        PublicKeyCredentialCreationOptions options =
                WebAuthnErrors.uncheck(() -> PublicKeyCredentialCreationOptions.fromJson(pending.optionsJson()));
        RegistrationResult result = WebAuthnErrors.uncheck(() -> relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                        .request(options)
                        .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                        .build()));
        RegisteredCredential registered = RegisteredCredential.builder()
                .credentialId(result.getKeyId().getId())
                .userHandle(credentials.ensureUserHandle(user))
                .publicKeyCose(result.getPublicKeyCose())
                .signatureCount(result.getSignatureCount())
                .build();
        String kind = pending.kind() != null ? pending.kind() : "passkey";
        String defaultLabel = "security_key".equals(kind) ? "Security key" : "Passkey";
        var factor = credentials.saveCredential(
                user.getId(), label != null && !label.isBlank() ? label : defaultLabel, registered,
                Map.of("aaguid", result.getAaguid().getHex(), "kind", kind));
        return Map.of(
                "factorId", factor.getId().toString(),
                "label", factor.getLabel(),
                "kind", kind);
    }

    /** Passkeys are allowed for sign-in (m_passkey) or as a two-step option of the app's policy. */
    private void assertPasskeysAvailable(UUID applicationId) {
        if (!effectiveSettings.getObject().isMethodAvailableForCurrentApplication("m_passkey")
                && !twoStepPolicy.passkeysAvailable(applicationId)) {
            throw new IllegalStateException("Passkeys are not enabled for this application.");
        }
    }

    /** The device must check a PIN or biometric, so the credential is two steps on its own. */
    private static AuthenticatorSelectionCriteria selection(String kind) {
        AuthenticatorSelectionCriteria.AuthenticatorSelectionCriteriaBuilder builder = AuthenticatorSelectionCriteria.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .residentKey(ResidentKeyRequirement.PREFERRED);
        if ("security_key".equals(kind)) {
            builder.authenticatorAttachment(com.yubico.webauthn.data.AuthenticatorAttachment.CROSS_PLATFORM);
        } else if ("passkey".equals(kind)) {
            builder.authenticatorAttachment(com.yubico.webauthn.data.AuthenticatorAttachment.PLATFORM);
        }
        return builder.build();
    }

    static String username(String tenantSlug, String email) {
        return tenantSlug.trim().toLowerCase(Locale.ROOT) + ":"
                + email.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayName(UserAccount user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getEmail();
    }
}
