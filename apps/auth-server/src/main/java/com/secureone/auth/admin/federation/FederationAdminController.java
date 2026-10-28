package com.secureone.auth.admin.federation;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.federation.IdentityProviderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Admin — federation", description = "Configure social, OIDC and SAML identity providers")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
public class FederationAdminController {

    private final IdentityProviderService providers;
    private final ApplicationRepository applications;

    public FederationAdminController(IdentityProviderService providers, ApplicationRepository applications) {
        this.providers = providers;
        this.applications = applications;
    }

    @GetMapping("/api/admin/v1/tenants/{tenantId}/federation/providers")
    public List<Map<String, Object>> listTenantProviders(@PathVariable UUID tenantId) {
        return providers.listPlatform(tenantId);
    }

    @PostMapping("/api/admin/v1/tenants/{tenantId}/federation/providers")
    public Map<String, Object> createTenantProvider(
            @PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return providers.create(tenantId, null, body);
    }

    @PutMapping("/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}")
    public Map<String, Object> updateTenantProvider(
            @PathVariable UUID tenantId, @PathVariable UUID providerId, @RequestBody Map<String, Object> body) {
        return providers.update(tenantId, providerId, body);
    }

    @DeleteMapping("/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTenantProvider(@PathVariable UUID tenantId, @PathVariable UUID providerId) {
        providers.delete(tenantId, providerId);
    }

    @GetMapping("/api/admin/v1/applications/{applicationId}/federation/providers")
    public List<Map<String, Object>> listApplicationProviders(@PathVariable UUID applicationId) {
        Application app = requireApplication(applicationId);
        return providers.listForApplication(app.getTenantId(), applicationId);
    }

    @PostMapping("/api/admin/v1/applications/{applicationId}/federation/providers")
    public Map<String, Object> createApplicationProvider(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        Application app = requireApplication(applicationId);
        return providers.create(app.getTenantId(), applicationId, body);
    }

    @PutMapping("/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}")
    public Map<String, Object> updateApplicationProvider(
            @PathVariable UUID applicationId,
            @PathVariable UUID providerId,
            @RequestBody Map<String, Object> body) {
        Application app = requireApplication(applicationId);
        return providers.update(app.getTenantId(), providerId, body);
    }

    @DeleteMapping("/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplicationProvider(@PathVariable UUID applicationId, @PathVariable UUID providerId) {
        Application app = requireApplication(applicationId);
        providers.delete(app.getTenantId(), providerId);
    }

    private Application requireApplication(UUID applicationId) {
        return applications
                .findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
    }
}
