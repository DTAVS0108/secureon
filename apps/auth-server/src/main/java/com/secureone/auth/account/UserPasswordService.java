package com.secureone.auth.account;

import com.secureone.auth.authn.AccountLockoutService;
import com.secureone.auth.user.UserCredential;
import com.secureone.auth.user.UserCredentialRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserPasswordService {

    private final UserCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService policy;
    /** Lazy: the lockout service needs beans that are built after this one. */
    private final ObjectProvider<AccountLockoutService> lockout;

    public UserPasswordService(
            UserCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService policy,
            ObjectProvider<AccountLockoutService> lockout) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.lockout = lockout;
    }

    public boolean hasPassword(UUID userId) {
        return credentials.findFirstByUserIdAndCurrentTrueOrderByCreatedAtDescIdDesc(userId).isPresent();
    }

    public void setPassword(UUID userId, String plainPassword) {
        setPassword(userId, plainPassword, null);
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword, UUID applicationId) {
        var cred = credentials
                .findFirstByUserIdAndCurrentTrueOrderByCreatedAtDescIdDesc(userId)
                .orElseThrow(() -> new BadCredentialsException("No password credential on file"));
        policy.assertNotExpired(cred);
        if (!passwordEncoder.matches(currentPassword, cred.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        setPassword(userId, newPassword, applicationId);
    }

    public void setPassword(UUID userId, String plainPassword, UUID applicationId) {
        policy.validateForSetPassword(userId, plainPassword, applicationId);
        String algorithm = policy.hashAlgorithm(applicationId);
        Instant expiresAt = policy.expiresAtForNewCredential(applicationId);

        credentials.clearCurrentForUser(userId);
        credentials.flush();

        UserCredential cred = new UserCredential();
        cred.setUserId(userId);
        cred.setPasswordHash(passwordEncoder.encode(plainPassword));
        cred.setAlgorithm(algorithm);
        cred.setExpiresAt(expiresAt);
        cred.setCurrent(true);
        credentials.saveAndFlush(cred);

        pruneHistory(userId, policy.historyRetentionCount(applicationId));
        // A new password (reset link or code, invitation, admin) proves control of the account: end any lockout,
        // as the account-locked email promises.
        lockout.ifAvailable(service -> service.clear(userId));
    }

    public void removePassword(UUID userId) {
        credentials.findByUserId(userId).forEach(credentials::delete);
        credentials.flush();
    }

    private void pruneHistory(UUID userId, int historyCount) {
        List<UserCredential> rows = credentials.findByUserIdOrderByCreatedAtDescIdDesc(userId);
        int keep = Math.max(1, historyCount > 0 ? historyCount : 1);
        if (rows.size() <= keep) {
            return;
        }
        List<UUID> deleteIds = new ArrayList<>();
        for (int i = keep; i < rows.size(); i++) {
            deleteIds.add(rows.get(i).getId());
        }
        if (!deleteIds.isEmpty()) {
            credentials.deleteByUserIdAndIdIn(userId, deleteIds);
        }
    }
}
