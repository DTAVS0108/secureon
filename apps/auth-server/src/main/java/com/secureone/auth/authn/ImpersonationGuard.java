package com.secureone.auth.authn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * While an administrator is signed in as a user, the user's own account cannot be changed: no new password,
 * email, two-step options, passkeys, sessions signed out, apps removed or deletion requested. The administrator
 * sees what the user sees; only the user changes their account.
 */
@Component
public class ImpersonationGuard implements HandlerInterceptor {

    private static final Set<String> CHANGES = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!CHANGES.contains(request.getMethod())) {
            return true;
        }
        String by = AuthContextAttributes.impersonatedBy(SecurityContextHolder.getContext().getAuthentication());
        if (by == null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"impersonation\",\"message\":"
                + "\"Not available while an administrator is signed in as this user.\"}");
        return false;
    }
}
