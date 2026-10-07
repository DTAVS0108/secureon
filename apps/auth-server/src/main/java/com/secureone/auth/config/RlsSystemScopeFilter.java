package com.secureone.auth.config;

import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sign-in and protocol endpoints must find a client, token, code or account by its secret
 * identifier before the tenant is known, so they run with platform row-level-security scope. Each
 * of them then checks tenant membership itself (for example, a user may only sign in to
 * applications of their own tenant). Everything else is limited to a tenant once
 * {@code secureone.rls.enforce} is on.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 26)
public class RlsSystemScopeFilter extends OncePerRequestFilter {

    /** Exact paths and path prefixes (ending in "/") that resolve identity across tenants. */
    static final List<String> SYSTEM_PATHS = List.of(
            "/oauth2/",
            "/userinfo",
            "/connect/",
            "/.well-known/",
            "/login",
            "/logout",
            "/api/admin/v1/auth/",
            "/api/v1/auth/",
            "/api/v1/account/password/forgot",
            "/api/v1/account/password/reset",
            "/api/v1/account/set-password",
            "/api/v1/account/email/",
            "/api/v1/account/verify/",
            "/api/v1/account/magic-link/",
            "/api/v1/m2m/",
            "/scim/v2/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isSystemPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        try {
            RlsScope.asPlatform(() -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException ex) {
                    throw new WrappedException(ex);
                }
                return null;
            });
        } catch (WrappedException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) ex.getCause();
        }
    }

    static boolean isSystemPath(String uri) {
        if (uri == null) {
            return false;
        }
        for (String path : SYSTEM_PATHS) {
            if (path.endsWith("/") ? uri.startsWith(path) : (uri.equals(path) || uri.startsWith(path + "/"))) {
                return true;
            }
        }
        return false;
    }

    private static final class WrappedException extends RuntimeException {
        WrappedException(Exception cause) {
            super(cause);
        }
    }
}
