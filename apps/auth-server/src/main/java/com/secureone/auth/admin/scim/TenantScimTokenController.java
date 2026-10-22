package com.secureone.auth.admin.scim;

import com.secureone.auth.scim.ScimTenantTokens;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-tenant SCIM provisioning token. Access: platform admin, or the tenant's super admin
 * (tenant governance path, see AdminAccessInterceptor).
 */
@Tag(name = "Admin — tenant SCIM", description = "SCIM 2.0 provisioning token for one tenant")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/scim")
public class TenantScimTokenController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    private final ScimTenantTokens tokens;
    private final String publicBaseUrl;

    public TenantScimTokenController(
            ScimTenantTokens tokens, @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.tokens = tokens;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    @GetMapping
    public Map<String, Object> status(@PathVariable UUID tenantId) {
        return Map.of("tokenConfigured", tokens.isConfigured(tenantId), "baseUrl", publicBaseUrl + "/scim/v2");
    }

    /** Creates or replaces the token. The raw token is returned only in this response. */
    @PostMapping("/token")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> issue(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            @PathVariable UUID tenantId) {
        stepUp.require(stepUpRequest); // a recent second step from the admin
        return Map.of("token", tokens.issue(tenantId), "baseUrl", publicBaseUrl + "/scim/v2");
    }

    @DeleteMapping("/token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID tenantId) {
        tokens.revoke(tenantId);
    }
}
