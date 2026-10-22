package com.secureone.auth.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Admin API authentication. The only accepted credential is an opaque console session token
 * ({@code Authorization: SecureOne-Admin-Session <token>}) issued by the console sign-in after the
 * password and second factor. Anything else (HTTP Basic, a hosted sign-in cookie) would skip MFA,
 * so it is ignored here and refused by {@link AdminAccessInterceptor}.
 */
@Component
public class AdminCredentialAuthenticationFilter extends OncePerRequestFilter {

    /** Opaque console session token issued by {@code POST /api/admin/v1/auth/login} (after MFA). */
    public static final String SESSION_SCHEME = "SecureOne-Admin-Session";

    /** Request attribute set when the request was authenticated with a console session token. */
    public static final String CONSOLE_SESSION_ATTR = AdminCredentialAuthenticationFilter.class.getName() + ".session";

    /** Request attribute: {@link java.time.Instant} at which the console session ends at the latest. */
    public static final String SESSION_EXPIRES_AT_ATTR = AdminCredentialAuthenticationFilter.class.getName() + ".expiresAt";

    private final AdminSessionService sessions;

    public AdminCredentialAuthenticationFilter(AdminSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/admin/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header != null
                && header.regionMatches(true, 0, SESSION_SCHEME + " ", 0, SESSION_SCHEME.length() + 1)) {
            String token = header.substring(SESSION_SCHEME.length() + 1).trim();
            sessions.resolveSession(token).ifPresent(session -> {
                SecurityContextHolder.getContext().setAuthentication(session.authentication());
                request.setAttribute(CONSOLE_SESSION_ATTR, Boolean.TRUE);
                request.setAttribute(AdminStepUpService.TOKEN_ATTR, token);
                request.setAttribute(SESSION_EXPIRES_AT_ATTR, session.expiresAt());
            });
        }
        filterChain.doFilter(request, response);
    }
}
