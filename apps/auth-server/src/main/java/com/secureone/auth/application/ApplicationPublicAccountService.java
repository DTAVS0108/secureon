package com.secureone.auth.application;

import com.secureone.auth.account.AccountNotificationService;
import com.secureone.auth.account.PasswordRecoveryService;
import com.secureone.auth.account.AccountVerificationService;
import com.secureone.auth.authn.AuthOtpService;
import com.secureone.auth.authn.AuthSessionService;
import com.secureone.auth.user.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationPublicAccountService {

    private final AccountNotificationService accounts;
    private final PasswordRecoveryService passwordRecovery;
    private final AccountVerificationService verification;
    private final AuthOtpService authOtp;
    private final AuthSessionService sessions;
    private final ApplicationTenantResolver tenants;
    private final ApplicationMembershipService memberships;

    public ApplicationPublicAccountService(
            AccountNotificationService accounts,
            PasswordRecoveryService passwordRecovery,
            AccountVerificationService verification,
            AuthOtpService authOtp,
            AuthSessionService sessions,
            ApplicationTenantResolver tenants,
            ApplicationMembershipService memberships) {
        this.accounts = accounts;
        this.passwordRecovery = passwordRecovery;
        this.verification = verification;
        this.authOtp = authOtp;
        this.sessions = sessions;
        this.tenants = tenants;
        this.memberships = memberships;
    }

    public Map<String, Object> forgotPassword(
            UUID applicationId, String identifier, String mode, String channel) {
        tenants.requireActiveApplication(applicationId);
        return passwordRecovery.requestRecovery(applicationId, identifier, mode, channel);
    }

    public Map<String, String> resetPasswordOtp(UUID applicationId, String challengeId, String code, String password) {
        tenants.requireActiveApplication(applicationId);
        return passwordRecovery.completeRecoveryOtp(applicationId, UUID.fromString(challengeId), code, password);
    }

    public Map<String, String> resendVerification(UUID applicationId, String email) {
        tenants.requireActiveApplication(applicationId);
        return accounts.resendVerificationForApplication(applicationId, email);
    }

    public Map<String, String> requestMagicLink(UUID applicationId, String email) {
        tenants.requireActiveApplication(applicationId);
        // Membership is granted after sign-in (LoginSuccessHandler), never on an unauthenticated request.
        return accounts.requestMagicLinkForApplication(applicationId, email);
    }

    public Map<String, Object> requestLoginOtp(UUID applicationId, String identifier, String channel) {
        tenants.requireActiveApplication(applicationId);
        return authOtp.requestLoginOtp(applicationId, identifier, channel);
    }

    // Sign-in steps run their own transactions; inside one shared transaction a rejected attempt marks
    // it rollback-only (500 on commit) and would roll back the failed-attempt counters too.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> verifyLoginOtp(
            UUID applicationId,
            String challengeId,
            String code,
            HttpServletRequest request,
            HttpServletResponse response) {
        tenants.requireActiveApplication(applicationId);
        return authOtp.verifyLoginOtp(applicationId, UUID.fromString(challengeId), code, request, response);
    }

    public Map<String, Object> confirmSignupOtp(UUID applicationId, String challengeId, String code, String channel) {
        tenants.requireActiveApplication(applicationId);
        var user = "phone".equalsIgnoreCase(channel) || "sms".equalsIgnoreCase(channel)
                ? verification.confirmPhoneOtp(UUID.fromString(challengeId), code, applicationId)
                : verification.confirmEmailOtp(UUID.fromString(challengeId), code, applicationId);
        memberships.ensureMemberWithDefaultRole(applicationId, user.getId());
        return Map.of("verified", true, "email", user.getEmail());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> sessionLogin(
            UUID applicationId, String identifier, String password, HttpServletRequest request, HttpServletResponse response) {
        tenants.requireActiveApplication(applicationId);
        String tenantSlug = tenants.requireTenantSlug(applicationId);
        // An unknown identifier goes through the same authentication as a known one, so the status,
        // message and work done do not reveal whether the account exists.
        String loginEmail = tenants.findUserByIdentifier(applicationId, identifier)
                .map(UserAccount::getEmail)
                .orElse(identifier);
        return sessions.sessionLogin(tenantSlug, loginEmail, password, applicationId, request, response);
    }
}
