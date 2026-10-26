package com.secureone.auth.admin.overview;

import com.secureone.auth.admin.AdminAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — overview", description = "Console home: sign-ins, active users, two-factor use, security events")
@RestController
public class PlatformOverviewController {

    private final PlatformOverviewService overview;
    private final AdminAccessService access;

    public PlatformOverviewController(PlatformOverviewService overview, AdminAccessService access) {
        this.overview = overview;
        this.access = access;
    }

    @GetMapping("/api/admin/v1/overview")
    public Map<String, Object> platform(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return overview.overview(null);
    }

    @GetMapping("/api/admin/v1/tenants/{tenantId}/overview")
    public Map<String, Object> tenant(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAsEmail, tenantId);
        return overview.overview(tenantId);
    }
}
