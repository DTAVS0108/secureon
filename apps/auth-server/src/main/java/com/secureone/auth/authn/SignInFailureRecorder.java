package com.secureone.auth.authn;

import com.secureone.auth.audit.AuditService;
import com.secureone.auth.logging.AuthRequestContext;
import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.session.LoginHistoryService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.webhook.WebhookEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * A failed sign-in, wherever it happened (hosted form, sign-in API): a FAILURE row in the user's sign-in history,
 * {@code user.login.failed} in the audit log (also for an unknown email of a known tenant, without naming whether
 * the account exists to the caller) and the {@code user.login.failed} webhook. Never changes the sign-in result.
 */
@Component
public class SignInFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(SignInFailureRecorder.class);

    public static final String ACTION = "user.login.failed";

    private final TenantUserDetailsService users;
    private final TenantRepository tenants;
    private final LoginHistoryService loginHistory;
    private final ObjectProvider<AuditService> audit;
    private final ObjectProvider<WebhookEventPublisher> webhooks;

    public SignInFailureRecorder(
            TenantUserDetailsService users,
            TenantRepository tenants,
            LoginHistoryService loginHistory,
            ObjectProvider<AuditService> audit,
            ObjectProvider<WebhookEventPublisher> webhooks) {
        this.users = users;
        this.tenants = tenants;
        this.loginHistory = loginHistory;
        this.audit = audit;
        this.webhooks = webhooks;
    }

    /** {@code principalName} is "tenant-slug:email"; {@code diagnostics} may be null. */
    public void record(
            HttpServletRequest request,
            String principalName,
            UUID applicationId,
            String reasonCode,
            String detail,
            Map<String, Object> diagnostics) {
        int sep = principalName == null ? -1 : principalName.indexOf(':');
        if (sep <= 0 || sep == principalName.length() - 1) {
            return;
        }
        String slug = principalName.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = principalName.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        String ip = ClientIpResolver.resolve(request);
        try {
            RlsScope.runAsPlatform(() -> {
                UserAccount account = account(slug, email);
                UUID tenantId = account != null
                        ? account.getTenantId()
                        : tenants.findBySlug(slug).map(t -> t.getId()).orElse(null);
                if (tenantId == null) {
                    return; // not one of our tenants: nothing to attribute it to
                }
                if (account != null) {
                    loginHistory.recordFailure(account, applicationId, ip, request.getHeader("User-Agent"),
                            AuthRequestContext.resolveSessionId(request), reasonCode, detail, diagnostics);
                }
                audit.ifAvailable(a -> a.recordIndependently(tenantId, applicationId, email, ACTION, "user",
                        account != null ? account.getId() : null, email + " (" + reasonCode + ")", false));
                if (account != null) {
                    webhooks.ifAvailable(p -> {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("userId", account.getId().toString());
                        payload.put("email", account.getEmail());
                        payload.put("reason", reasonCode);
                        payload.put("ipAddress", ip);
                        p.publish(account.getTenantId(), applicationId, ACTION, payload);
                    });
                }
            });
        } catch (RuntimeException e) {
            log.warn("Failed sign-in for {} not recorded: {}", principalName, e.getMessage());
        }
    }

    private UserAccount account(String slug, String email) {
        try {
            return users.resolveAccount(slug, email);
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    /** Short reason code for an authentication failure (no account details). */
    public static String reasonOf(org.springframework.security.core.AuthenticationException ex) {
        if (ex instanceof org.springframework.security.authentication.LockedException) {
            return "locked";
        }
        if (ex instanceof org.springframework.security.authentication.CredentialsExpiredException) {
            return "password_expired";
        }
        if (ex instanceof org.springframework.security.authentication.DisabledException) {
            return "disabled";
        }
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof org.springframework.security.core.userdetails.UsernameNotFoundException) {
            return "unknown_or_inactive";
        }
        return "bad_credentials";
    }
}
