package com.secureone.auth.authn;

import com.secureone.auth.logging.AuthRequestContext;
import com.secureone.auth.tenant.TenantRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final TenantRepository tenants;
    private final SignInApplicationResolver signInApplication;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.protection.AttackProtectionService> attackProtection;

    private final SignInFailureRecorder failures;

    public FormLoginFailureHandler(
            RequestCache requestCache,
            TenantRepository tenants,
            SignInApplicationResolver signInApplication,
            SignInFailureRecorder failures) {
        this.signInApplication = signInApplication;
        this.requestCache = requestCache;
        this.tenants = tenants;
        this.failures = failures;
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
        // History row, audit entry and webhook; the app being signed in to, so it shows in that app's lists.
        failures.record(request, snapshot.usernameField(), signInApplication.current(),
                reasonCode, exception.getMessage(), diagnostics);
    }
}
