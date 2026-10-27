package com.secureone.auth.admin.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.saml.SamlIdpController;
import com.secureone.auth.saml.SamlIdpCredentialService;
import com.secureone.auth.saml.SamlServiceProviderService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
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
 * An application's SAML apps (service providers that sign users in through the tenant's SAML identity provider):
 * Settings › Integration, reading needs {@code app:read}, changes {@code settings:write}. The application's access
 * rules decide who may sign in to them.
 */
@Tag(name = "Admin — application SAML apps", description = "SAML service providers signing in through SecureOne")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/applications/{applicationId}/settings/saml-apps")
public class ApplicationSamlAppController {

    private final SamlServiceProviderService serviceProviders;
    private final SamlIdpCredentialService credentials;
    private final SamlIdpController idp;
    private final ApplicationRepository applications;
    private final TenantRepository tenants;
    private final ObjectProvider<AuditService> audit;

    public ApplicationSamlAppController(
            SamlServiceProviderService serviceProviders, SamlIdpCredentialService credentials, SamlIdpController idp,
            ApplicationRepository applications, TenantRepository tenants, ObjectProvider<AuditService> audit) {
        this.serviceProviders = serviceProviders;
        this.credentials = credentials;
        this.idp = idp;
        this.applications = applications;
        this.tenants = tenants;
        this.audit = audit;
    }

    @Operation(summary = "The SAML apps and the identity provider they use (entity ID, SSO URL, metadata, certificate)")
    @GetMapping
    public Map<String, Object> list(@PathVariable UUID applicationId) {
        UUID tenantId = tenantOf(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("identityProvider", identityProvider(tenantId));
        out.put("serviceProviders", serviceProviders.list(applicationId).stream().map(serviceProviders::view).toList());
        out.put("attributeSources", new java.util.TreeSet<>(SamlServiceProviderService.SOURCES));
        out.put("defaultAttributes", SamlServiceProviderService.DEFAULT_ATTRIBUTES);
        return out;
    }

    @Operation(summary = "Add a SAML app (name, entityId, acsUrl; optional sloUrl, nameIdFormat, attributes, spCertificate)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantOf(applicationId);
        var sp = serviceProviders.create(tenantId, applicationId, body);
        audited(tenantId, applicationId, "saml_app.created", sp);
        return serviceProviders.view(sp);
    }

    @Operation(summary = "Change a SAML app (fields left out keep their values)")
    @PutMapping("/{spId}")
    public Map<String, Object> update(@PathVariable UUID applicationId, @PathVariable UUID spId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantOf(applicationId);
        var sp = serviceProviders.update(applicationId, spId, body);
        audited(tenantId, applicationId, "saml_app.updated", sp);
        return serviceProviders.view(sp);
    }

    @Operation(summary = "Remove a SAML app (it can no longer sign users in)")
    @DeleteMapping("/{spId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID applicationId, @PathVariable UUID spId) {
        UUID tenantId = tenantOf(applicationId);
        audited(tenantId, applicationId, "saml_app.deleted", serviceProviders.delete(applicationId, spId));
    }

    private Map<String, Object> identityProvider(UUID tenantId) {
        String slug = RlsScope.asPlatform(() -> tenants.findById(tenantId)).map(t -> t.getSlug()).orElseThrow();
        String entityId = idp.entityId(slug);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityId", entityId);
        out.put("metadataUrl", entityId + "/metadata");
        out.put("ssoUrl", entityId + "/sso");
        out.put("sloUrl", entityId + "/slo");
        out.put("initUrl", entityId + "/init?sp=");
        out.put("certificatePem", SamlIdpCredentialService.pemOf(credentials.credential(tenantId).certificate()));
        out.put("bindings", List.of("HTTP-Redirect", "HTTP-POST"));
        return out;
    }

    private UUID tenantOf(UUID applicationId) {
        return applications.findById(applicationId)
                .map(a -> a.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private void audited(UUID tenantId, UUID applicationId, String action, SamlServiceProviderService.ServiceProvider sp) {
        audit.ifAvailable(a -> a.record(tenantId, applicationId, "admin", action, "saml_service_provider", sp.id(),
                sp.name() + " (" + sp.entityId() + ")", true));
    }
}
