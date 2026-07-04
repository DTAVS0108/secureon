package com.secureone.auth.config;

import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSchemaContext;
import com.secureone.auth.tenant.TenantContext;
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

/**
 * Resolves tenant scope for PostgreSQL row-level security. Runs after {@link ApplicationSchemaFilter}
 * so application-scoped routes can inherit the application's tenant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_ADMIN_PATH =
            Pattern.compile("^/api/admin/v1/tenants/([0-9a-fA-F-]{36})(?:/|$)");

    private final ApplicationRepository applications;

    public TenantContextFilter(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            resolveTenantId(request).ifPresent(TenantContext::setTenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private java.util.Optional<UUID> resolveTenantId(HttpServletRequest request) {
        UUID fromPath = tenantFromPath(request.getRequestURI());
        if (fromPath != null) {
            return java.util.Optional.of(fromPath);
        }
        UUID fromQuery = tenantFromParameter(request.getParameter("tenantId"));
        if (fromQuery != null) {
            return java.util.Optional.of(fromQuery);
        }
        UUID fromHeader = tenantFromParameter(request.getHeader("X-Tenant-Id"));
        if (fromHeader != null) {
            return java.util.Optional.of(fromHeader);
        }
        UUID applicationId = ApplicationSchemaContext.getApplicationId();
        if (applicationId != null) {
            return applications.findById(applicationId).map(app -> app.getTenantId());
        }
        return java.util.Optional.empty();
    }

    static UUID tenantFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Matcher matcher = TENANT_ADMIN_PATH.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        return parseUuid(matcher.group(1));
    }

    private static UUID tenantFromParameter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseUuid(raw.trim());
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
