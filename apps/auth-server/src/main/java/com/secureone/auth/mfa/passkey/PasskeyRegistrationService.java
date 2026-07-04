package com.secureone.auth.mfa.passkey;

import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.RegisteredCredential;
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

    public PasskeyRegistrationService(
            RelyingParty relyingParty,
            WebAuthnCredentialRepository credentials,
            WebAuthnChallengeStore challenges,
            AuthSettingsService authSettings) {
        this.relyingParty = relyingParty;
        this.credentials = credentials;
        this.challenges = challenges;
        this.authSettings = authSettings;
    }

    public Map<String, Object> beginRegistration(UserAccount user, String tenantSlug, String label) {
        assertPasskeysAvailable();
        String username = username(tenantSlug, user.getEmail());
        ByteArray userHandle = credentials.ensureUserHandle(user);
        PublicKeyCredentialCreationOptions options = WebAuthnErrors.uncheck(() -> relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(username)
                                .displayName(displayName(user))
                                .id(userHandle)
                                .build())
                        .build()));
        String requestId = challenges.saveRegistration(new WebAuthnChallengeStore.RegistrationPending(
                user.getId(), username, WebAuthnErrors.uncheck(options::toJson)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", requestId);
        out.put("publicKey", WebAuthnErrors.uncheck(options::toCredentialsCreateJson));
        return out;
    }

    public Map<String, Object> finishRegistration(
            UserAccount user, UUID requestId, String credentialJson, String label) {
        assertPasskeysAvailable();
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
        var factor = credentials.saveCredential(
                user.getId(), label, registered, Map.of("aaguid", result.getAaguid().getHex()));
        return Map.of(
                "factorId", factor.getId().toString(),
                "label", factor.getLabel());
    }

    private void assertPasskeysAvailable() {
        if (!authSettings.isAuthMethodEnabled("m_passkey") || !authSettings.isAuthMethodImplemented("m_passkey")) {
            throw new IllegalStateException("Passkeys are not enabled on this platform.");
        }
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
