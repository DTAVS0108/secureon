package com.secureone.auth.authn;

import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSchemaContext;
import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.mfa.MfaLoginService.MfaChallengeResponse;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.logging.AuthRequestContext;
import com.secureone.auth.session.LoginHistoryService;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.webhook.WebhookEventPublisher;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final RequestCache requestCache;

    private final TenantUserDetailsService userDetailsService;
    private final UserAccountRepository users;
    private final LoginHistoryService loginHistory;
    private final UserApplicationRepository userApplications;
    private final ApplicationAccessService access;
    private final ApplicationRepository applications;
    private final TenantRepository tenants;
    private final AccountLockoutService lockout;
    private final MfaLoginService mfaLogin;
    private final ObjectProvider<WebhookEventPublisher> webhookPublisher;
    private final SignInApplicationResolver signInApplication;

    public LoginSuccessHandler(
            RequestCache requestCache,
            TenantUserDetailsService userDetailsService,
            UserAccountRepository users,
            LoginHistoryService loginHistory,
            UserApplicationRepository userApplications,
            ApplicationAccessService access,
            ApplicationRepository applications,
            TenantRepository tenants,
            AccountLockoutService lockout,
            MfaLoginService mfaLogin,
            ObjectProvider<WebhookEventPublisher> webhookPublisher,
            SignInApplicationResolver signInApplication) {
        this.signInApplication = signInApplication;
        this.requestCache = requestCache;
        this.userDetailsService = userDetailsService;
        this.users = users;
        this.loginHistory = loginHistory;
        this.userApplications = userApplications;
        this.access = access;
        this.applications = applications;
        this.tenants = tenants;
        this.lockout = lockout;
        this.mfaLogin = mfaLogin;
        this.webhookPublisher = webhookPublisher;
        setDefaultTargetUrl("/login.html?signedIn=1");
    }

    private void finishLogin(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        String authorizeUrl =
                OAuthLoginRedirectSupport.resolvePendingAuthorizeUrl(request, response, requestCache);
        if (authorizeUrl != null) {
            requestCache.removeRequest(request, response);
            getRedirectStrategy().sendRedirect(request, response, authorizeUrl);
            return;
        }
        String signedInUrl = "/login.html?signedIn=1";
        String applicationId = request.getParameter("applicationId");
        if (applicationId != null && !applicationId.isBlank()) {
            signedInUrl += "&applicationId=" + URLEncoder.encode(applicationId.trim(), StandardCharsets.UTF_8);
        }
        String continueParam = OAuthLoginRedirectSupport.readContinueParam(request);
        if (continueParam != null) {
            signedInUrl = OAuthLoginRedirectSupport.appendContinueToLoginUrl(signedInUrl, continueParam);
        }
        getRedirectStrategy().sendRedirect(request, response, signedInUrl);
    }

    public void recordSuccessfulLogin(HttpServletRequest request, Authentication authentication) {
        String username = authentication.getName();
        AuthRequestContext.enrichForLogin(request, username, tenants, signInApplication.current());
        if (username == null || !username.contains(":")) {
            return;
        }
        int sep = username.indexOf(':');
        UserAccount account =
                userDetailsService.resolveAccount(username.substring(0, sep), username.substring(sep + 1));
        lockout.recordSuccess(account.getId());
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(Instant.now());
        users.save(account);
        UUID applicationId = resolveApplicationId(request, account);
        if (applicationId != null) {
            // Enrolls only when the app allows self registration; OAuth authorize enforces the result.
            access.admit(applicationId, account.getId());
        }
        loginHistory.record(
                account,
                applicationId,
                "SUCCESS",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "password",
                "—",
                AuthRequestContext.resolveSessionId(request),
                null);
        AuthContextAttributes.recordPasswordAuth(request);
        log.info(
                "Form login succeeded [sess={}] for {}",
                AuthRequestContext.formatSessionRef(request),
                username);
        publishLoginSucceededWebhook(account, applicationId, request);
    }

    private void publishLoginSucceededWebhook(UserAccount account, UUID applicationId, HttpServletRequest request) {
        webhookPublisher.ifAvailable(publisher -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", account.getId().toString());
            payload.put("email", account.getEmail());
            payload.put("tenantId", account.getTenantId().toString());
            if (applicationId != null) {
                payload.put("applicationId", applicationId.toString());
            }
            payload.put("ipAddress", request.getRemoteAddr());
            payload.put("userAgent", request.getHeader("User-Agent"));
            publisher.publish(account.getTenantId(), applicationId, "user.login.succeeded", payload);
        });
    }

    /** Requested app (form param, else app-scoped URL), only if it belongs to the user's tenant. */
    private UUID resolveApplicationId(HttpServletRequest request, UserAccount account) {
        UUID requested = null;
        String raw = request.getParameter("applicationId");
        if (raw != null && !raw.isBlank()) {
            try {
                requested = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (requested == null) {
            requested = ApplicationSchemaContext.getApplicationId();
        }
        // The authorization request this sign-in completes names the app authoritatively.
        UUID signingInTo = signInApplication.current();
        if (signingInTo != null) {
            requested = signingInTo;
        }
        if (requested != null) {
            UUID candidate = requested;
            return applications.findById(candidate)
                    .filter(app -> app.getTenantId().equals(account.getTenantId()))
                    .map(app -> candidate)
                    .orElse(null);
        }
        return userApplications.findByUserId(account.getId()).stream()
                .map(ua -> ua.getApplicationId())
                .findFirst()
                .orElse(null);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        // The app the sign-in is for decides MFA; a missing or crafted form field must not skip it.
        UUID applicationId = signInApplication.current();
        if (mfaLogin.enrollmentRequiredButMissing(authentication, applicationId)) {
            mfaLogin.clearSession(request, response);
            String url = "/login.html?error=mfa_enrollment_required"
                    + (applicationId != null ? "&applicationId=" + applicationId : "");
            getRedirectStrategy().sendRedirect(request, response, url);
            return;
        }
        if (mfaLogin.shouldChallengeFormLogin(authentication, applicationId, request)) {
            int sep = authentication.getName().indexOf(':');
            UserAccount account = userDetailsService.resolveAccount(
                    authentication.getName().substring(0, sep), authentication.getName().substring(sep + 1));
            MfaChallengeResponse challenge = mfaLogin.issueChallenge(
                    account, authentication.getName().substring(0, sep).trim().toLowerCase(), applicationId);
            mfaLogin.clearSession(request, response);
            String mfaUrl = "/login.html?mfa=1&mfaChallengeId="
                    + URLEncoder.encode(challenge.mfaChallengeId().toString(), StandardCharsets.UTF_8)
                    + (applicationId != null
                            ? "&applicationId=" + URLEncoder.encode(applicationId.toString(), StandardCharsets.UTF_8)
                            : "");
            getRedirectStrategy().sendRedirect(request, response, mfaUrl);
            return;
        }
        recordSuccessfulLogin(request, authentication);
        finishLogin(request, response, authentication);
    }

}
