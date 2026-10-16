package com.secureone.auth.authn;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.application.ApplicationSchemaContext;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Which application a sign-in is for, so its sign-in rules (methods, lockout, MFA) apply. Sources,
 * most authoritative first:
 * <ol>
 *   <li>an application-specific URL ({@code /api/v1/applications/{id}/...});
 *   <li>the OAuth authorization request saved in the session when the user was sent to sign in, which
 *       is the app the resulting code will be issued to;
 *   <li>the request's client_id/Referer context, then an {@code applicationId} form field.
 * </ol>
 * Client-supplied values come last so a crafted form post cannot pick a sibling app with weaker rules.
 * Must run inside the security filter chain (after the session is restored).
 */
@Component
public class SignInApplicationResolver {

    private static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";
    private static final Pattern APPLICATION_PATH =
            Pattern.compile("^/api/(?:admin/v1|v1)/applications/[0-9a-fA-F-]{36}(?:/|$)");

    private final ApplicationOAuthClientResolver clients;

    public SignInApplicationResolver(ApplicationOAuthClientResolver clients) {
        this.clients = clients;
    }

    public UUID current() {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
        UUID context = ApplicationSchemaContext.getApplicationId();
        if (request == null) {
            return context;
        }
        if (context != null && APPLICATION_PATH.matcher(request.getRequestURI()).find()) {
            return context;
        }
        UUID saved = fromSavedAuthorizationRequest(request);
        if (saved != null) {
            return saved;
        }
        if (context != null) {
            return context;
        }
        String raw = request.getParameter("applicationId");
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private UUID fromSavedAuthorizationRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SAVED_REQUEST) instanceof SavedRequest saved)) {
            return null;
        }
        String[] clientIds = saved.getParameterValues("client_id");
        if (clientIds == null || clientIds.length == 0 || clientIds[0] == null || clientIds[0].isBlank()) {
            return null;
        }
        return RlsScope.asPlatform(() -> clients.findActiveByOAuthClientId(clientIds[0]))
                .map(Application::getId)
                .orElse(null);
    }
}
