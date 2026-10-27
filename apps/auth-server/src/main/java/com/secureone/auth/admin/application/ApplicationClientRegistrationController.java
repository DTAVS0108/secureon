package com.secureone.auth.admin.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.oauth.registration.ClientRegistrationService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantIssuers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
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

/**
 * An application's dynamic client registration (Settings › Integration: reading needs {@code app:read}, changes
 * {@code settings:write}): the mode and policies, and initial access tokens (shown once).
 */
@Tag(name = "Admin — application client registration", description = "Dynamic client registration settings and initial access tokens")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/applications/{applicationId}/settings/client-registration")
public class ApplicationClientRegistrationController {

    private final ClientRegistrationService registrations;
    private final ApplicationRepository applications;
    private final OAuthClientRepository clients;
    private final TenantIssuers issuers;

    public ApplicationClientRegistrationController(ClientRegistrationService registrations, ApplicationRepository applications,
            OAuthClientRepository clients, TenantIssuers issuers) {
        this.registrations = registrations;
        this.applications = applications;
        this.clients = clients;
        this.issuers = issuers;
    }

    @Operation(summary = "Mode (off, token, open), policies, endpoints and the number of registered clients")
    @GetMapping
    public Map<String, Object> get(@PathVariable UUID applicationId) {
        return view(applicationId, registrations.policy(applicationId, tenantOf(applicationId)));
    }

    @Operation(
            summary = "Save the mode and policies",
            description = "mode: off | token (an initial access token is needed) | open (no token; the tenant issuer's endpoint, "
                    + "one application per tenant). allowedScopes, allowedGrantTypes (authorization_code, refresh_token, "
                    + "client_credentials; not with open), trustedHosts (redirect URI hosts, *.example.com allowed; empty: any), "
                    + "maxClients, unusedDays (0: keep unused clients).")
    @PutMapping
    public Map<String, Object> save(@PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return view(applicationId, registrations.savePolicy(applicationId, tenantOf(applicationId), body));
    }

    @Operation(summary = "Initial access tokens (values are shown only when created)")
    @GetMapping("/tokens")
    public List<ClientRegistrationService.InitialToken> tokens(@PathVariable UUID applicationId) {
        tenantOf(applicationId);
        return registrations.tokens(applicationId);
    }

    @Operation(summary = "Create an initial access token: label, maxUses (1-1000), expiresInHours (1-8760); the value is shown once")
    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createToken(@PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantOf(applicationId);
        int maxUses = body.get("maxUses") instanceof Number n ? n.intValue() : 1;
        int hours = body.get("expiresInHours") instanceof Number n ? n.intValue() : 24;
        var created = registrations.createToken(applicationId, tenantId,
                body.get("label") == null ? null : String.valueOf(body.get("label")), maxUses, hours);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", created.token());
        out.put("value", created.value());
        return out;
    }

    @Operation(summary = "Revoke an initial access token (clients it registered stay)")
    @DeleteMapping("/tokens/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeToken(@PathVariable UUID applicationId, @PathVariable UUID tokenId) {
        registrations.revokeToken(applicationId, tenantOf(applicationId), tokenId);
    }

    private Map<String, Object> view(UUID applicationId, ClientRegistrationService.Policy policy) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", policy.mode());
        out.put("allowedScopes", policy.allowedScopes());
        out.put("allowedGrantTypes", policy.allowedGrantTypes());
        out.put("trustedHosts", policy.trustedHosts());
        out.put("maxClients", policy.maxClients());
        out.put("unusedDays", policy.unusedDays());
        out.put("registeredClients", RlsScope.asPlatform(() -> clients.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(c -> c.isDynamicallyRegistered()).count()));
        String tenantIssuer = issuers.issuersOf(policy.tenantId()).stream().filter(i -> i.contains("/t/")).findFirst()
                .orElse(issuers.sharedIssuer());
        out.put("registrationEndpoint", issuers.sharedIssuer() + "/oauth2/register");
        // Open registration needs no token, so it is on the tenant's own issuer (it names the tenant).
        out.put("openRegistrationEndpoint", tenantIssuer + "/oauth2/register");
        return out;
    }

    private UUID tenantOf(UUID applicationId) {
        return RlsScope.asPlatform(() -> applications.findById(applicationId))
                .map(a -> a.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }
}
