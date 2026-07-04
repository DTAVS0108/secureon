package com.secureone.auth.federation;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.federation.FederationProviderType;
import com.secureone.auth.federation.IdentityProvider;
import com.secureone.auth.federation.IdentityProviderRepository;
import com.secureone.auth.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Public auth — federation", description = "Social / OIDC sign-in")
@RestController
@RequestMapping("/api/v1/auth/federation")
public class FederationAuthController {

    private final IdentityProviderService providers;
    private final FederationLoginService login;
    private final TenantRepository tenants;
    private final ApplicationRepository applications;
    private final IdentityProviderRepository providerRepository;

    @Value("${secureone.public-base-url:http://localhost:9000}")
    private String publicBaseUrl;

    public FederationAuthController(
            IdentityProviderService providers,
            FederationLoginService login,
            TenantRepository tenants,
            ApplicationRepository applications,
            IdentityProviderRepository providerRepository) {
        this.providers = providers;
        this.login = login;
        this.tenants = tenants;
        this.applications = applications;
        this.providerRepository = providerRepository;
    }

    @Operation(summary = "List enabled federation providers for a tenant/application")
    @GetMapping("/providers")
    public List<Map<String, Object>> providers(
            @RequestParam(required = false) String tenantSlug, @RequestParam(required = false) UUID applicationId) {
        UUID tenantId = resolveTenantId(tenantSlug, applicationId);
        return providers.listPublic(tenantId, applicationId);
    }

    @Operation(summary = "Start federation login (redirects to external IdP)")
    @GetMapping("/{alias}/start")
    public ResponseEntity<Void> start(
            @PathVariable String alias,
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false, name = "continue") String continueUrl,
            HttpServletResponse response) {
        UUID tenantId = resolveTenantId(tenantSlug, applicationId);
        String redirect = login.startLogin(tenantId, applicationId, alias, continueUrl, response);
        return ResponseEntity.status(302).header("Location", redirect).build();
    }

    @Operation(summary = "OAuth/OIDC callback from external IdP")
    @GetMapping("/{alias}/callback")
    public ResponseEntity<?> callback(
            @PathVariable String alias,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (error != null && !error.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Federation login denied: " + error);
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authorization code or state.");
        }
        return login.handleCallback(alias, code, state, request, response);
    }

    @Operation(summary = "SAML SP metadata stub for a configured provider")
    @GetMapping(value = "/saml/{alias}/metadata", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> samlMetadata(
            @PathVariable String alias,
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) UUID applicationId) {
        UUID tenantId = resolveTenantId(tenantSlug, applicationId);
        IdentityProvider provider = resolveSamlProvider(tenantId, applicationId, alias);
        String entityId = publicBaseUrl + "/api/v1/auth/federation/saml/" + provider.getAlias();
        String acsUrl = publicBaseUrl + "/api/v1/auth/federation/saml/" + provider.getAlias() + "/acs";
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
                  <SPSSODescriptor AuthnRequestsSigned="false" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>
                    <AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="%s" index="0"/>
                  </SPSSODescriptor>
                </EntityDescriptor>
                """
                .formatted(entityId, acsUrl);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    @Operation(summary = "SAML login (not yet available)")
    @GetMapping("/saml/{alias}/start")
    public ResponseEntity<Map<String, String>> samlStart(@PathVariable String alias) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "error", "not_implemented",
                        "message", "SAML 2.0 federation is planned for a future release.",
                        "alias", alias));
    }

    private IdentityProvider resolveSamlProvider(UUID tenantId, UUID applicationId, String alias) {
        Optional<IdentityProvider> provider;
        if (applicationId != null) {
            provider = providerRepository.findByTenantIdAndApplicationIdAndAliasIgnoreCase(
                    tenantId, applicationId, alias);
        } else {
            provider = providerRepository.findByTenantIdAndApplicationIdIsNullAndAliasIgnoreCase(tenantId, alias);
        }
        return provider
                .filter(row -> FederationProviderType.SAML.name().equalsIgnoreCase(row.getType()))
                .filter(IdentityProvider::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SAML provider not found."));
    }

    private UUID resolveTenantId(String tenantSlug, UUID applicationId) {
        if (applicationId != null) {
            Application app = applications.findById(applicationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
            return app.getTenantId();
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantSlug or applicationId is required.");
        }
        return tenants.findBySlug(tenantSlug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found."))
                .getId();
    }
}
