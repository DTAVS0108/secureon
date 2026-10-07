package com.secureone.auth.config;

import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantContext;
import com.secureone.auth.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Pins the row-level-security scope of a signed-in tenant user (console operator or end user,
 * principal {@code tenantSlug:email}) to their own tenant, whatever {@code tenantId} the request
 * names. Platform super admins (ROLE_ADMIN) keep the scope {@link TenantContextFilter} chose.
 */
@Component
public class OperatorTenantScopeInterceptor implements HandlerInterceptor {

    private final TenantRepository tenants;

    public OperatorTenantScopeInterceptor(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return true;
        }
        boolean platformAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        int sep = auth.getName().indexOf(':');
        if (platformAdmin || sep <= 0) {
            return true;
        }
        String slug = auth.getName().substring(0, sep).trim().toLowerCase(Locale.ROOT);
        RlsScope.asPlatform(() -> tenants.findBySlug(slug))
                .ifPresent(tenant -> TenantContext.setTenantId(tenant.getId()));
        return true;
    }
}
