package com.secureone.auth.admin.scim;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.scim.ScimSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — SCIM", description = "Platform SCIM provisioning settings")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/settings/scim")
public class ScimAdminController {

    private final ScimSettingsService scim;
    private final AdminAccessService access;

    public ScimAdminController(ScimSettingsService scim, AdminAccessService access) {
        this.scim = scim;
        this.access = access;
    }

    @GetMapping
    public Map<String, Object> get(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return scim.getConfig();
    }

    @PutMapping
    public Map<String, Object> save(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody Map<String, Object> body) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return scim.saveConfig(body != null ? body : Map.of());
    }
}
