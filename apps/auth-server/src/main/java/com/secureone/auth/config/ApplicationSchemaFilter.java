package com.secureone.auth.config;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.application.ApplicationSchemaContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

/** Resolves application id from URL and applies per-app PostgreSQL search_path for the request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApplicationSchemaFilter extends OncePerRequestFilter {

    private static final Pattern APPLICATION_PATH = Pattern.compile(
            "^/(?:api/(?:admin/v1|v1)/applications/([0-9a-fA-F-]{36}))(?:/|$)");

    private final ApplicationOAuthClientResolver applications;

    public ApplicationSchemaFilter(ApplicationOAuthClientResolver applications) {
        this.applications = applications;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UUID applicationId = resolveApplicationId(request);
            if (applicationId != null) {
                ApplicationSchemaContext.setApplicationId(applicationId);
            }
            filterChain.doFilter(request, response);
        } finally {
            ApplicationSchemaContext.clear();
        }
    }

    private UUID resolveApplicationId(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null) {
            Matcher matcher = APPLICATION_PATH.matcher(path);
            if (matcher.find()) {
                try {
                    return UUID.fromString(matcher.group(1));
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
            // Console calls on shared admin routes (e.g. PUT /api/admin/v1/users/{id}) name the app in
            // a header; AdminAccessInterceptor checks the operator may access it.
            if (path.startsWith("/api/admin/v1/")) {
                String header = request.getHeader("X-Application-Id");
                if (header != null && !header.isBlank()) {
                    try {
                        return UUID.fromString(header.trim());
                    } catch (IllegalArgumentException ignored) {
                        // fall through
                    }
                }
            }
        }
        String clientId = request.getParameter("client_id");
        if (clientId == null || clientId.isBlank()) {
            String referer = request.getHeader("Referer");
            if (referer != null && referer.contains("client_id=")) {
                try {
                    clientId = UriComponentsBuilder.fromUriString(referer)
                            .build(true)
                            .getQueryParams()
                            .getFirst("client_id");
                } catch (IllegalArgumentException ignored) {
                    clientId = null;
                }
            }
        }
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return applications.findActiveByOAuthClientId(clientId).map(Application::getId).orElse(null);
    }
}
