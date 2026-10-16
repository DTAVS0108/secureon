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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final FederationStateStore states;
    private final SamlLoginService saml;
    private final SamlLogoutService samlLogout;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FederationAuthController.class);

    public FederationAuthController(
            IdentityProviderService providers,
            FederationLoginService login,
            TenantRepository tenants,
            ApplicationRepository applications,
            IdentityProviderRepository providerRepository,
            FederationStateStore states,
            SamlLoginService saml,
            SamlLogoutService samlLogout) {
        this.states = states;
        this.saml = saml;
        this.samlLogout = samlLogout;
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

    @Operation(summary = "Start federation login (redirects to the external IdP: OAuth/OIDC or SAML)")
    @GetMapping("/{alias}/start")
    public ResponseEntity<?> start(
            @PathVariable String alias,
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false, name = "continue") String continueUrl,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID tenantId = resolveTenantId(tenantSlug, applicationId);
        return login.startLogin(tenantId, applicationId, alias, continueUrl, request, response);
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
        // A browser lands here: every failure goes back to the sign-in page with a reason it can show,
        // for the same application and OAuth request, never to an error page.
        Optional<FederationStateStore.PendingState> pending = states.peek(state);
        if (error != null && !error.isBlank()) {
            states.consume(state);
            return backToSignIn(pending, "cancelled");
        }
        if (code == null || code.isBlank() || pending.isEmpty()) {
            return backToSignIn(pending, "expired");
        }
        try {
            return login.handleCallback(alias, code, state, request, response);
        } catch (IllegalArgumentException | IllegalStateException | ResponseStatusException ex) {
            String message = String.valueOf(ex.getMessage());
            String reason = message.contains("verified email") ? "unverified_email"
                    : message.contains("not allowed") || message.contains("disabled") ? "not_allowed"
                    : "failed";
            log.warn("Federation sign-in via {} failed: {}", alias, message);
            return backToSignIn(pending, reason);
        }
    }

    private ResponseEntity<?> backToSignIn(Optional<FederationStateStore.PendingState> pending, String reason) {
        StringBuilder url = new StringBuilder("/login.html?error=federation&reason=").append(reason);
        pending.ifPresent(p -> {
            if (p.applicationId() != null) {
                url.append("&applicationId=").append(p.applicationId());
            }
            if (p.continueUrl() != null && !p.continueUrl().isBlank()) {
                url.append("&continue=").append(java.net.URLEncoder.encode(p.continueUrl(), java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url.toString()).build();
    }

    @Operation(summary = "SAML service-provider metadata for a configured provider (give this to the IdP)")
    @GetMapping(value = "/saml/{alias}/metadata", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> samlMetadata(
            @PathVariable String alias,
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) UUID applicationId) {
        UUID tenantId = resolveTenantId(tenantSlug, applicationId);
        IdentityProvider provider = resolveSamlProvider(tenantId, applicationId, alias);
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(saml.metadata(provider));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @Operation(summary = "SAML login (same as /{alias}/start for a SAML provider)")
    @GetMapping("/saml/{alias}/start")
    public ResponseEntity<?> samlStart(
            @PathVariable String alias,
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false, name = "continue") String continueUrl,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID tenantId = resolveTenantId(tenantSlug, applicationId);
        resolveSamlProvider(tenantId, applicationId, alias);
        return login.startLogin(tenantId, applicationId, alias, continueUrl, request, response);
    }

    @Operation(summary = "SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)")
    @RequestMapping(value = "/saml/slo/{providerId}", method = {org.springframework.web.bind.annotation.RequestMethod.GET,
            org.springframework.web.bind.annotation.RequestMethod.POST})
    public ResponseEntity<?> samlSingleLogout(
            @PathVariable UUID providerId, HttpServletRequest request, HttpServletResponse response) {
        return samlLogout.handle(providerId, request, response);
    }

    @Operation(summary = "Sign out; for a SAML session also at the IdP (single logout)")
    @GetMapping("/saml/logout")
    public ResponseEntity<?> samlLogout(HttpServletRequest request, HttpServletResponse response) {
        return samlLogout.startLogout(request, response);
    }

    @Operation(summary = "SAML assertion consumer service (HTTP-POST binding from the IdP)")
    @PostMapping(value = "/saml/acs/{providerId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> samlAcs(
            @PathVariable UUID providerId,
            @RequestParam(name = "RelayState", required = false) String relayState,
            HttpServletRequest request,
            HttpServletResponse response) {
        // A browser lands here: every failure goes back to the sign-in page with a reason, as for OIDC.
        Optional<FederationStateStore.PendingState> pending = states.peek(relayState);
        if (pending.isEmpty()) {
            // No sign-in of ours is waiting: an IdP-initiated response (if the provider allows it), or expired.
            try {
                return login.handleUnsolicitedSamlResponse(providerId, request, response);
            } catch (IllegalArgumentException | IllegalStateException | ResponseStatusException ex) {
                log.warn("Unsolicited SAML response for provider {} refused: {}", providerId, ex.getMessage());
                return backToSignIn(pending, "expired");
            }
        }
        try {
            return login.handleSamlResponse(providerId, relayState, request, response);
        } catch (IllegalArgumentException | IllegalStateException | ResponseStatusException ex) {
            states.consume(relayState);
            String message = String.valueOf(ex.getMessage());
            String reason = message.contains("verified email") ? "unverified_email"
                    : message.contains("not allowed") || message.contains("disabled") || message.contains("granted access")
                            ? "not_allowed"
                    : "failed";
            log.warn("SAML sign-in via provider {} failed: {}", providerId, message);
            return backToSignIn(pending, reason);
        }
    }

    private IdentityProvider resolveSamlProvider(UUID tenantId, UUID applicationId, String alias) {
        Optional<IdentityProvider> provider;
        if (applicationId != null) {
            provider = providerRepository.findByTenantIdAndApplicationIdAndAliasIgnoreCase(
                    tenantId, applicationId, alias)
                    .or(() -> providerRepository.findByTenantIdAndApplicationIdIsNullAndAliasIgnoreCase(tenantId, alias));
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
