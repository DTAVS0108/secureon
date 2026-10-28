package com.secureone.auth.authn;

import com.secureone.auth.application.ApplicationTenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Builds internal {@code tenant:email} usernames for the hosted login form. When {@code applicationId}
 * is present, tenant slug is resolved server-side so end users only enter email and password.
 */
@Component
public class TenantLoginUsernameFilter extends OncePerRequestFilter {

    private final ApplicationTenantResolver applications;

    public TenantLoginUsernameFilter(ApplicationTenantResolver applications) {
        this.applications = applications;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isLoginPost(request)) {
            // An application's own tenant always wins over a tenant typed or posted by the browser.
            String appTenant = resolveTenantFromApplicationId(normalize(request.getParameter("applicationId")));
            String username = normalize(request.getParameter("username"));
            String email = normalize(request.getParameter("email"));
            if (appTenant != null) {
                String login = email != null ? email : stripTenantPrefix(username);
                if (login != null) {
                    request = new UsernameOverrideRequest(request, appTenant + ":" + login.toLowerCase(Locale.ROOT));
                }
            } else if (username == null) {
                String tenant = normalize(request.getParameter("tenant"));
                if (tenant != null && email != null) {
                    request = new UsernameOverrideRequest(request, tenant + ":" + email.toLowerCase(Locale.ROOT));
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveTenantFromApplicationId(String applicationId) {
        if (applicationId == null) {
            return null;
        }
        try {
            return applications.requireTenantSlug(UUID.fromString(applicationId));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** {@code tenant:user@example.com} -> {@code user@example.com}; a plain value is returned as-is. */
    private static String stripTenantPrefix(String username) {
        if (username == null) {
            return null;
        }
        int sep = username.indexOf(':');
        return sep >= 0 ? normalize(username.substring(sep + 1)) : username;
    }

    private static boolean isLoginPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getRequestURI());
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class UsernameOverrideRequest extends HttpServletRequestWrapper {

        private final Map<String, String[]> params;

        UsernameOverrideRequest(HttpServletRequest request, String username) {
            super(request);
            Map<String, String[]> merged = new HashMap<>(request.getParameterMap());
            merged.put("username", new String[] {username});
            params = Collections.unmodifiableMap(merged);
        }

        @Override
        public String getParameter(String name) {
            String[] values = params.get(name);
            if (values == null || values.length == 0) {
                return null;
            }
            return values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return params;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(params.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return params.get(name);
        }
    }
}
