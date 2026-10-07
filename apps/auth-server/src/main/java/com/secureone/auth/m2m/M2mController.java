package com.secureone.auth.m2m;

import com.secureone.auth.serviceaccount.ServiceAccountContext;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/v1/m2m")
public class M2mController {

    private final TenantRepository tenants;

    public M2mController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        requireContext();
        return Map.of("status", "UP", "service", "secureone-m2m");
    }

    @GetMapping("/whoami")
    public Map<String, Object> whoami() {
        ServiceAccountContext context = requireContext();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantId", context.tenantId().toString());
        out.put("serviceAccountId", context.serviceAccountId().toString());
        out.put("apiKeyId", context.apiKeyId().toString());
        out.put("name", context.name());
        out.put("scopes", ServiceAccountContext.defaultScopes());
        return out;
    }

    @GetMapping("/tenant")
    public Map<String, Object> tenant() {
        ServiceAccountContext context = requireContext();
        Tenant tenant = tenants.findById(context.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", tenant.getId().toString());
        out.put("slug", tenant.getSlug());
        out.put("name", tenant.getName());
        out.put("status", tenant.getStatus());
        return out;
    }

    private static ServiceAccountContext requireContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof ServiceAccountContext context)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "M2M authentication required.");
        }
        return context;
    }
}
