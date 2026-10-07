package com.secureone.auth.scim;

import com.secureone.auth.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates /scim/v2 calls. A tenant SCIM token (issued per tenant) binds the request to that
 * tenant; the platform token from SCIM settings keeps the legacy cross-tenant behaviour.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
public class ScimBearerAuthFilter extends OncePerRequestFilter {

    private final ScimSettingsService settings;
    private final ScimTenantTokens tenantTokens;

    public ScimBearerAuthFilter(ScimSettingsService settings, ScimTenantTokens tenantTokens) {
        this.settings = settings;
        this.tenantTokens = tenantTokens;
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
            reject(response, HttpStatus.NOT_FOUND, "SCIM is disabled.");
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response, HttpStatus.UNAUTHORIZED, "Bearer token required.");
            return;
        }
        String token = auth.substring(7).trim();

        Optional<UUID> tenant = tenantTokens.resolve(token);
        if (tenant.isPresent()) {
            request.setAttribute(ScimScope.TENANT_ATTRIBUTE, tenant.get());
            TenantContext.setTenantId(tenant.get());
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        String platformToken = settings.bearerToken();
        if (!platformToken.isBlank() && MessageDigest.isEqual(
                platformToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(response, HttpStatus.UNAUTHORIZED, "Invalid bearer token.");
    }

    /** SCIM error body (RFC 7644 3.12), written directly so it is not re-dispatched to /error. */
    private static void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/scim+json");
        response.getWriter().write("{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"],\"status\":\""
                + status.value() + "\",\"detail\":\"" + detail + "\"}");
    }
}
