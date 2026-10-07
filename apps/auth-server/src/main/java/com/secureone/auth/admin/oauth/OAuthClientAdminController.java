package com.secureone.auth.admin.oauth;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateResult;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientSecretResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — OAuth clients", description = "Register and manage OAuth/OIDC client credentials")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/oauth-clients")
public class OAuthClientAdminController {

    private final OAuthClientAdminService service;
    private final AdminAccessService access;

    public OAuthClientAdminController(OAuthClientAdminService service, AdminAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public List<OAuthClientResponse> list(
            Authentication authentication,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID applicationId) {
        if (applicationId == null) {
            access.requireSuperAdmin(authentication);
        }
        // With applicationId, AdminAccessInterceptor has already checked access to that application;
        // its operators may see (never edit) the app's clients. Secrets are never returned here.
        return service.list(applicationId == null ? tenantId : null, applicationId);
    }

    @GetMapping("/{id}")
    public OAuthClientResponse get(Authentication authentication, @PathVariable UUID id) {
        access.requireSuperAdmin(authentication);
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientCreateResult create(
            Authentication authentication, @Valid @RequestBody OAuthClientCreateRequest request) {
        access.requireSuperAdmin(authentication);
        return service.create(request);
    }

    @PutMapping("/{id}")
    public OAuthClientResponse update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody OAuthClientUpdateRequest request) {
        access.requireSuperAdmin(authentication);
        return service.update(id, request);
    }

    @PostMapping("/{id}/rotate-secret")
    public OAuthClientSecretResponse rotateSecret(Authentication authentication, @PathVariable UUID id) {
        access.requireSuperAdmin(authentication);
        return service.rotateClientSecret(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID id) {
        access.requireSuperAdmin(authentication);
        service.delete(id);
    }
}
