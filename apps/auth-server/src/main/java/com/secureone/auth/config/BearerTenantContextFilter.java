package com.secureone.auth.config;

import com.secureone.auth.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token account APIs run in the token's tenant: the signed {@code tenant_id} claim of a user access
 * token sets the row-level-security scope before any transaction opens, so lookups (including by the
 * account UUID when a client's {@code sub} is the user id) see that tenant's rows and nothing else.
 */
public class BearerTenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID tenantId = null;
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            String claim = jwt.getToken().getClaimAsString("tenant_id");
            if (claim != null) {
                try {
                    tenantId = UUID.fromString(claim);
                } catch (IllegalArgumentException ignored) {
                    // not a user token from this server
                }
            }
        }
        if (tenantId == null) {
            chain.doFilter(request, response);
            return;
        }
        UUID previous = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.setTenantId(previous);
        }
    }
}
