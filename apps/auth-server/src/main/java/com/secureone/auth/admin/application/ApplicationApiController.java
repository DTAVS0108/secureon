package com.secureone.auth.admin.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationApiService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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

/**
 * An application's APIs (Settings › Integration: reading needs {@code app:read}, changes {@code settings:write}).
 * Access tokens name an API in {@code aud} when they carry one of its scopes; give a client those scopes so it can
 * ask for them.
 */
@Tag(name = "Admin — application APIs", description = "APIs (resource servers) an application's access tokens are for")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/applications/{applicationId}/settings/apis")
public class ApplicationApiController {

    private final ApplicationApiService apis;
    private final ApplicationRepository applications;
    private final ObjectProvider<AuditService> audit;

    public ApplicationApiController(
            ApplicationApiService apis, ApplicationRepository applications, ObjectProvider<AuditService> audit) {
        this.apis = apis;
        this.applications = applications;
        this.audit = audit;
    }

    @Operation(summary = "The application's APIs")
    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID applicationId) {
        tenantOf(applicationId);
        return apis.list(applicationId).stream().map(ApplicationApiService.Api::toMap).toList();
    }

    @Operation(summary = "Add an API (name, identifier, scopes)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantOf(applicationId);
        ApplicationApiService.Api api = apis.create(tenantId, applicationId, body);
        audited(tenantId, applicationId, "api.created", api);
        return api.toMap();
    }

    @Operation(summary = "Change an API")
    @PutMapping("/{apiId}")
    public Map<String, Object> update(
            @PathVariable UUID applicationId, @PathVariable UUID apiId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantOf(applicationId);
        ApplicationApiService.Api api = apis.update(tenantId, applicationId, apiId, body);
        audited(tenantId, applicationId, "api.updated", api);
        return api.toMap();
    }

    @Operation(summary = "Remove an API (new tokens stop naming it; tokens already issued keep it until they expire)")
    @DeleteMapping("/{apiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID applicationId, @PathVariable UUID apiId) {
        UUID tenantId = tenantOf(applicationId);
        audited(tenantId, applicationId, "api.deleted", apis.delete(applicationId, apiId));
    }

    private UUID tenantOf(UUID applicationId) {
        return applications.findById(applicationId)
                .map(a -> a.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private void audited(UUID tenantId, UUID applicationId, String action, ApplicationApiService.Api api) {
        audit.ifAvailable(a -> a.record(tenantId, applicationId, "admin", action, "api", api.id(),
                api.name() + " (" + api.identifier() + ")", true));
    }
}
