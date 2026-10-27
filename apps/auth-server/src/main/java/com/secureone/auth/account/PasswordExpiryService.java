package com.secureone.auth.account;

import com.secureone.auth.notify.ApplicationEmailContextFactory;
import com.secureone.auth.notify.EmailTokenType;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserCredential;
import com.secureone.auth.user.UserCredentialRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordExpiryService {

    public static final String EXPIRED_LOGIN_MESSAGE =
            "Your password has expired and you cannot sign in. A password reset link has been sent to your email address.";

    private static final Logger log = LoggerFactory.getLogger(PasswordExpiryService.class);

    private final UserCredentialRepository credentials;
    private final UserAccountRepository users;
    private final PasswordPolicyService policy;
    private final AccountNotificationService notifications;
    private final ApplicationEmailContextFactory emailContext;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailTokenService tokens;

    public PasswordExpiryService(
            UserCredentialRepository credentials,
            UserAccountRepository users,
            PasswordPolicyService policy,
            AccountNotificationService notifications,
            ApplicationEmailContextFactory emailContext) {
        this.credentials = credentials;
        this.users = users;
        this.policy = policy;
        this.notifications = notifications;
        this.emailContext = emailContext;
    }

    /** The change token (and an expired-password notice) are kept although the sign-in is refused. */
    @Transactional(noRollbackFor = org.springframework.security.core.AuthenticationException.class)
    public void enforceLoginAllowed(UserAccount user, UserCredential credential, UUID applicationId) {
        if (credential != null && credential.isMustChange() && tokens != null) {
            // Called only after the password was checked: the user proved they hold the temporary password.
            throw new PasswordChangeRequiredException(tokens.issue(user, EmailTokenType.RESET_PASSWORD).rawToken());
        }
        if (credential == null || credential.getExpiresAt() == null) {
            return;
        }
        if (!PasswordPolicyRules.isExpired(credential, Instant.now())) {
            return;
        }
        sendExpiredResetIfNeeded(user, credential, resolveApplicationId(applicationId, user));
        throw new CredentialsExpiredException(EXPIRED_LOGIN_MESSAGE);
    }

    @Transactional
    public int processScheduledNotifications() {
        Instant now = Instant.now();
        int actions = 0;
        for (UserCredential credential : credentials.findActiveWithExpiry()) {
            UserAccount user = users.findById(credential.getUserId()).orElse(null);
            if (user == null || !isActiveUser(user)) {
                continue;
            }
            UUID applicationId = emailContext.resolveApplicationId(null, user);
            if (PasswordPolicyRules.isExpired(credential, now)) {
                if (sendExpiredResetIfNeeded(user, credential, applicationId)) {
                    actions++;
                }
            } else if (sendExpiringSoonIfNeeded(user, credential, applicationId, now)) {
                actions++;
            }
        }
        return actions;
    }

    private boolean sendExpiringSoonIfNeeded(
            UserAccount user, UserCredential credential, UUID applicationId, Instant now) {
        if (credential.getExpiryWarningSentAt() != null) {
            return false;
        }
        if (!PasswordPolicyRules.isExpiringSoon(credential, policy.resolvePolicy(applicationId), now)) {
            return false;
        }
        try {
            notifications.sendPasswordExpiringSoonEmail(applicationId, user, credential.getExpiresAt());
            credential.setExpiryWarningSentAt(now);
            credentials.save(credential);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to send password expiry warning to {} (application={}): {}",
                    user.getEmail(),
                    applicationId,
                    ex.getMessage());
            return false;
        }
    }

    private boolean sendExpiredResetIfNeeded(UserAccount user, UserCredential credential, UUID applicationId) {
        if (credential.getExpiryExpiredNoticeSentAt() != null) {
            return false;
        }
        try {
            notifications.sendExpiredPasswordResetEmail(applicationId, user);
            credential.setExpiryExpiredNoticeSentAt(Instant.now());
            credentials.save(credential);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to send expired-password reset to {} (application={}): {}",
                    user.getEmail(),
                    applicationId,
                    ex.getMessage());
            return false;
        }
    }

    private UUID resolveApplicationId(UUID applicationId, UserAccount user) {
        return applicationId != null ? applicationId : emailContext.resolveApplicationId(null, user);
    }

    private static boolean isActiveUser(UserAccount user) {
        return user.getStatus() == null || !"SUSPENDED".equalsIgnoreCase(user.getStatus());
    }
}
