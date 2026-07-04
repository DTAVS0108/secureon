package com.secureone.auth.account;

import com.secureone.auth.application.PasswordPolicySanitizer;
import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserCredential;
import com.secureone.auth.user.UserCredentialRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyService {

    private final AuthSettingsService authSettings;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;

    private final LeakedPasswordService leakedPasswords;

    public PasswordPolicyService(
            AuthSettingsService authSettings,
            ApplicationEffectiveSettingsService effectiveSettings,
            UserCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            LeakedPasswordService leakedPasswords) {
        this.authSettings = authSettings;
        this.effectiveSettings = effectiveSettings;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.leakedPasswords = leakedPasswords;
    }

    public void validate(String password) {
        validateForSetPassword(null, password, null);
    }

    public void validate(String password, UUID applicationId) {
        validateForSetPassword(null, password, applicationId);
    }

    public void validateForSetPassword(UUID userId, String password, UUID applicationId) {
        Map<String, Object> policy = resolvePolicy(applicationId);
        PasswordPolicyRules.validate(password, policy);
        if (userId != null) {
            List<UserCredential> previous = credentials.findByUserIdOrderByCreatedAtDescIdDesc(userId);
            PasswordPolicyRules.validateNotInHistory(password, previous, passwordEncoder, policy);
        }
        leakedPasswords.assertNotLeaked(password, PasswordPolicySanitizer.breachCheckEnabled(policy));
    }

    public void assertNotExpired(UserCredential credential) {
        if (PasswordPolicyRules.isExpired(credential, Instant.now())) {
            throw new CredentialsExpiredException("Password has expired. Reset your password to continue.");
        }
    }

    public Map<String, Object> resolvePolicy(UUID applicationId) {
        return applicationId != null
                ? effectiveSettings.passwordPolicy(applicationId)
                : authSettings.getPasswordPolicy();
    }

    public Map<String, Object> publicPolicy(UUID applicationId) {
        return PasswordPolicyRules.publicPolicy(resolvePolicy(applicationId));
    }

    public List<Map<String, Object>> evaluate(String password, UUID applicationId) {
        return PasswordPolicyRules.evaluate(password, resolvePolicy(applicationId));
    }

    public String hashAlgorithm(UUID applicationId) {
        return PasswordPolicyRules.hashAlgorithm(resolvePolicy(applicationId));
    }

    public Instant expiresAtForNewCredential(UUID applicationId) {
        return PasswordPolicyRules.expiresAtForNewCredential(resolvePolicy(applicationId), Instant.now());
    }

    public int historyRetentionCount(UUID applicationId) {
        return PasswordPolicyRules.historyCount(resolvePolicy(applicationId));
    }
}
