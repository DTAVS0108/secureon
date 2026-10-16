package com.secureone.auth.application;

import com.secureone.auth.account.AccountNotificationService;
import com.secureone.auth.account.AccountVerificationService;
import com.secureone.auth.account.PasswordPolicyService;
import com.secureone.auth.account.PhoneNormalizer;
import com.secureone.auth.account.UserPasswordService;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.webhook.UserLifecycleWebhookPublisher;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service registration for end users of a registered application (e.g. an online-store client
 * integrated via the public API).
 */
@Service
@Transactional
public class ApplicationSignupService {

    private final ApplicationRepository applications;
    private final UserAccountRepository users;
    private final ApplicationMembershipService memberships;
    private final UserPasswordService passwords;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final AccountNotificationService accountNotifications;
    private final AccountVerificationService accountVerification;
    private final AuditService auditService;
    private final UserLifecycleWebhookPublisher userLifecycleWebhooks;
    private final String publicBaseUrl;
    private final PasswordPolicyService passwordPolicy;
    private final ApplicationSettingsService settings;
    private final VerificationPolicyService verificationPolicy;

    public ApplicationSignupService(
            ApplicationRepository applications,
            UserAccountRepository users,
            ApplicationMembershipService memberships,
            UserPasswordService passwords,
            ApplicationEffectiveSettingsService effectiveSettings,
            AccountNotificationService accountNotifications,
            AccountVerificationService accountVerification,
            AuditService auditService,
            UserLifecycleWebhookPublisher userLifecycleWebhooks,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl,
            PasswordPolicyService passwordPolicy,
            ApplicationSettingsService settings,
            VerificationPolicyService verificationPolicy) {
        this.verificationPolicy = verificationPolicy;
        this.passwordPolicy = passwordPolicy;
        this.settings = settings;
        this.applications = applications;
        this.users = users;
        this.memberships = memberships;
        this.passwords = passwords;
        this.effectiveSettings = effectiveSettings;
        this.accountNotifications = accountNotifications;
        this.accountVerification = accountVerification;
        this.auditService = auditService;
        this.userLifecycleWebhooks = userLifecycleWebhooks;
        this.publicBaseUrl =
                publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> signupOptions(UUID applicationId) {
        Application app = requireActiveApplication(applicationId);
        boolean enabled = isSignupEnabled(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", app.getId().toString());
        out.put("applicationName", app.getName());
        out.put("signupEnabled", enabled);
        out.put("signupEndpoint", "/api/v1/applications/" + applicationId + "/signup");
        out.put("hostedSignupPage", publicBaseUrl + "/account/signup.html?applicationId=" + applicationId);
        out.put("loginPage", publicBaseUrl + "/login.html?applicationId=" + applicationId);
        out.put(
                "passwordPolicy",
                com.secureone.auth.account.PasswordPolicyRules.signupPolicy(
                        effectiveSettings.passwordPolicy(applicationId)));
        out.put("loginEmailHint", "your@email.com");
        out.put("account", ApplicationAccountEndpoints.manifestBlock(applicationId));
        out.put("verification", accountVerification.verificationOptions(applicationId));
        return out;
    }

    public Map<String, Object> register(UUID applicationId, ApplicationSignupDtos.SignupRequest request) {
        Application app = requireActiveApplication(applicationId);
        ensureSignupAllowed(applicationId);

        String email = ApplicationConsumerAuth.normalizeEmail(request.email());
        // Input checks come first so a taken email or phone gets the same 400s as a new one.
        passwordPolicy.validate(request.password(), applicationId);
        String phone = null;
        if (request.phone() != null && !request.phone().isBlank()) {
            phone = PhoneNormalizer.toE164(request.phone(), verificationPolicy.defaultPhoneRegion(applicationId));
            if (phone == null) {
                throw new IllegalArgumentException("Invalid phone number.");
            }
        }
        // A taken email or phone answers exactly like a new sign-up (anyone could otherwise test which
        // emails and numbers have accounts), changes nothing, and the inbox owner is told instead.
        var existing = users.findByTenantIdAndEmail(app.getTenantId(), email);
        if (existing.isPresent()) {
            accountNotifications.notifySignupWithExistingEmail(applicationId, existing.get(), forgotPasswordLink(applicationId));
            return signupResponse(email, accountVerification.decoySignupVerification(
                    applicationId, request.verificationMode(), request.verificationChannel(), email, phone));
        }
        if (phone != null && users.findByTenantIdAndPhone(app.getTenantId(), phone).isPresent()) {
            accountNotifications.notifySignupPhoneInUse(
                    applicationId,
                    email,
                    resolveDisplayName(request.firstName(), request.lastName(), request.displayName(), email),
                    publicBaseUrl + "/account/signup.html?applicationId=" + applicationId);
            return signupResponse(email, accountVerification.decoySignupVerification(
                    applicationId, request.verificationMode(), request.verificationChannel(), email, phone));
        }

        UserAccount user = new UserAccount();
        user.setTenantId(app.getTenantId());
        user.setEmail(email);
        user.setUsername(resolveUsername(email));
        user.setDisplayName(resolveDisplayName(request.firstName(), request.lastName(), request.displayName(), email));
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        if (phone != null) {
            user.setPhone(phone);
        }
        user.setStatus("ACTIVE");
        user.setType("USER");
        users.saveAndFlush(user);

        passwords.setPassword(user.getId(), request.password(), applicationId);

        memberships.ensureMemberWithDefaultRole(applicationId, user.getId());

        Map<String, Object> verification = accountVerification.sendSignupVerification(
                applicationId, user, "signup", request.verificationMode(), request.verificationChannel());

        auditService.record(
                app.getTenantId(),
                applicationId,
                email,
                "user.self_registered",
                "user_account",
                user.getId(),
                email,
                true);

        userLifecycleWebhooks.userCreated(user, applicationId, "self_registration");

        return signupResponse(email, verification);
    }

    /** One response shape for every sign-up outcome (no user id: a refused duplicate has none to give). */
    private static Map<String, Object> signupResponse(String email, Map<String, Object> verification) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "message",
                "otp".equals(verification.get("mode"))
                        ? "Account created. Enter the verification code sent to your "
                                + ("sms".equals(verification.get("channel")) ? "phone" : "email")
                                + "."
                        : "Account created. Check your inbox for a verification link, then sign in with your email and password.");
        response.put("loginEmail", email);
        response.put("emailVerificationRequired", true);
        response.put("verificationSent", true);
        response.putAll(verification);
        response.put(
                "devMailInboxHint",
                "Local dev: open http://localhost:8025 (MailHog) if the message is not in your real inbox.");
        return response;
    }

    /** The app's own forgot-password page when it has one, otherwise the hosted page. */
    private String forgotPasswordLink(UUID applicationId) {
        Object configured = settings.publicClientUrls(applicationId).get("forgotPassword");
        if (configured != null && !configured.toString().isBlank()) {
            return configured.toString().trim();
        }
        return publicBaseUrl + "/account/forgot-password.html?applicationId=" + applicationId;
    }

    private void ensureSignupAllowed(UUID applicationId) {
        if (!isSignupEnabled(applicationId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Sign-up is not enabled for this application. Ask the app administrator to enable the Self Registration feature flag.");
        }
        if (!effectiveSettings.isAuthMethodEnabled(applicationId, "m_password")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Password sign-up is not available for this application.");
        }
        if (!effectiveSettings.isAuthMethodImplemented(applicationId, "m_password")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Password sign-up is not available for this application.");
        }
    }

    private boolean isSignupEnabled(UUID applicationId) {
        return effectiveSettings.isFeatureEnabled(applicationId, "self_registration");
    }

    private Application requireActiveApplication(UUID applicationId) {
        Application app = applications
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
        return app;
    }

    private static String resolveUsername(String email) {
        int at = email.indexOf('@');
        if (at > 0) {
            String local = email.substring(0, at).replaceAll("[^a-zA-Z0-9._-]", "");
            if (!local.isBlank()) {
                return local;
            }
        }
        return email;
    }

    private static String resolveDisplayName(
            String firstName, String lastName, String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isBlank() ? email : combined;
    }
}
