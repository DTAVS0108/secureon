package com.secureone.auth.scim;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
public class ScimBearerAuthFilter extends OncePerRequestFilter {

    private final ScimSettingsService settings;

    public ScimBearerAuthFilter(ScimSettingsService settings) {
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/scim/v2/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!settings.enabled()) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "SCIM is disabled.");
            return;
        }
        String expected = settings.bearerToken();
        if (expected.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "SCIM bearer token is not configured.");
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bearer token required.");
            return;
        }
        String token = auth.substring("Bearer ".length()).trim();
        if (!expected.equals(token)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
