package com.secureone.auth.authn;

import com.secureone.auth.logging.AuthRequestContext;
import com.secureone.auth.session.LoginHistoryService;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;

@Component
public class FormLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(FormLoginFailureHandler.class);

    private final RequestCache requestCache;
    private final TenantUserDetailsService userDetailsService;
    private final LoginHistoryService loginHistory;
    private final TenantRepository tenants;
    private final SignInApplicationResolver signInApplication;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.protection.AttackProtectionService> attackProtection;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.webhook.WebhookEventPublisher> webhooks;

    public FormLoginFailureHandler(
            RequestCache requestCache,
            TenantUserDetailsService userDetailsService,
            LoginHistoryService loginHistory,
            TenantRepository tenants,
            SignInApplicationResolver signInApplication) {
        this.signInApplication = signInApplication;
        this.requestCache = requestCache;
        this.userDetailsService = userDetailsService;
        this.loginHistory = loginHistory;
        this.tenants = tenants;
        setDefaultFailureUrl("/login.html?error");
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String authorizeUrl =
                OAuthLoginRedirectSupport.resolvePendingAuthorizeUrl(request, response, requestCache);
        if (exception instanceof com.secureone.auth.account.PasswordChangeRequiredException change) {
            // A temporary password: straight to choosing their own, then they sign in (and continue to the app).
            String target = "/account/reset-password.html?temporary=1&token="
                    + URLEncoder.encode(change.changeToken(), StandardCharsets.UTF_8);
            String applicationId = request.getParameter("applicationId");
            if (applicationId != null && !applicationId.isBlank()) {
                target += "&applicationId=" + URLEncoder.encode(applicationId.trim(), StandardCharsets.UTF_8);
            }
            if (authorizeUrl != null && !authorizeUrl.isBlank()) {
                target += "&continue=" + URLEncoder.encode(authorizeUrl, StandardCharsets.UTF_8);
            }
            response.sendRedirect(target);
            return;
        }
        String errorParam = exception instanceof org.springframework.security.authentication.CredentialsExpiredException
                ? "error=expired"
                : "error";
        String failureUrl = "/login.html?" + errorParam;
        String applicationId = request.getParameter("applicationId");
        if (applicationId != null && !applicationId.isBlank()) {
            failureUrl += "&applicationId=" + URLEncoder.encode(applicationId.trim(), StandardCharsets.UTF_8);
        }
        setDefaultFailureUrl(OAuthLoginRedirectSupport.appendContinueToLoginUrl(failureUrl, authorizeUrl));

        LoginAttemptDiagnostics.Snapshot snapshot = LoginAttemptDiagnostics.capture(request);
        String username = snapshot.usernameField();
        AuthRequestContext.enrichForLogin(request, username, tenants, signInApplication.current());
        String reasonCode = LoginAttemptDiagnostics.classify(exception, snapshot);
        LoginAttemptDiagnostics.applyMdc(snapshot, reasonCode, exception);
        Map<String, Object> diagnostics = LoginAttemptDiagnostics.toMetadata(snapshot, reasonCode, exception);
        log.warn(
                "Form login failed [sess={}] {}",
                AuthRequestContext.formatSessionRef(request),
                LoginAttemptDiagnostics.formatLogLine(snapshot, reasonCode, exception));
        recordFailure(request, snapshot, reasonCode, exception, diagnostics);
        // Repeated failures from one address bring up the CAPTCHA (Attack protection › sign-in after failures).
        if (attackProtection != null) attackProtection.ifAvailable(p -> p.recordSignInFailure(com.secureone.auth.logging.ClientIpResolver.resolve(request)));
        super.onAuthenticationFailure(request, response, exception);
    }

    private void recordFailure(
            HttpServletRequest request,
            LoginAttemptDiagnostics.Snapshot snapshot,
            String reasonCode,
            AuthenticationException exception,
            Map<String, Object> diagnostics) {
        String username = snapshot.usernameField();
        if (username == null || !username.contains(":")) {
            return;
        }
        try {
            int sep = username.indexOf(':');
            UserAccount account = userDetailsService.resolveAccount(
                    username.substring(0, sep).trim().toLowerCase(Locale.ROOT),
                    username.substring(sep + 1).trim().toLowerCase(Locale.ROOT));
            if (webhooks != null) webhooks.ifAvailable(p -> {
                java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("userId", account.getId().toString());
                payload.put("email", account.getEmail());
                payload.put("reason", reasonCode);
                payload.put("ipAddress", com.secureone.auth.logging.ClientIpResolver.resolve(request));
                try {
                    p.publish(account.getTenantId(), signInApplication.current(), "user.login.failed", payload);
                } catch (RuntimeException ignored) {
                    // A webhook never changes the sign-in result.
                }
            });
            loginHistory.recordFailure(
                    account,
                    // The app being signed in to, so the failure shows in that app's Sessions list.
                    signInApplication.current(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    AuthRequestContext.resolveSessionId(request),
                    reasonCode,
                    exception.getMessage(),
                    diagnostics);
        } catch (RuntimeException ignored) {
            // Unknown tenant/user — still logged at WARN above with full request diagnostics.
        }
    }
}
