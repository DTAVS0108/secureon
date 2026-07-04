package com.secureone.auth.config.oauth;

import com.secureone.auth.oauth.RefreshTokenReuseContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Captures refresh tokens on the OAuth token endpoint for reuse detection auditing. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RefreshTokenReuseAuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isRefreshTokenGrant(request)) {
            RefreshTokenReuseContext.setPresentedRefreshToken(request.getParameter("refresh_token"));
            try {
                filterChain.doFilter(request, response);
            } finally {
                RefreshTokenReuseContext.clear();
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isRefreshTokenGrant(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || !path.endsWith("/oauth2/token")) {
            return false;
        }
        return "refresh_token".equals(request.getParameter("grant_type"));
    }
}
