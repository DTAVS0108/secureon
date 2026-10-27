package com.secureone.auth.oauth.registration;

import com.secureone.auth.tenant.TenantIssuers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic client registration (RFC 7591; OIDC Dynamic Client Registration 1.0 at /connect/register) and client
 * self-management with the registration access token (RFC 7592). Also under a tenant issuer (/t/slug/...) and on a
 * tenant's custom domain. Answers in the RFCs' JSON (error, error_description), never cached.
 */
@Tag(name = "Client registration", description = "Apps register themselves as OAuth clients (RFC 7591, RFC 7592)")
@RestController
public class ClientRegistrationController {

    private final ClientRegistrationService registrations;
    private final TenantIssuers issuers;

    public ClientRegistrationController(ClientRegistrationService registrations, TenantIssuers issuers) {
        this.registrations = registrations;
        this.issuers = issuers;
    }

    @Operation(
            summary = "Register a client (RFC 7591)",
            description = "Authorization: Bearer <initial access token>, unless the organization takes open registrations "
                    + "(then on its own issuer only). The application's policies decide what may be asked for; the response "
                    + "has client_id, client_secret (confidential clients), registration_access_token and registration_client_uri.")
    @PostMapping(value = {"/oauth2/register", "/connect/register"}, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> register(HttpServletRequest request, @RequestBody Map<String, Object> metadata) {
        TenantIssuers.Target target = target(request);
        Map<String, Object> body = registrations.register(bearer(request), target == null ? null : target.tenantId(),
                request.getRemoteAddr(), metadata, base(target));
        return noStore(ResponseEntity.status(HttpStatus.CREATED)).body(body);
    }

    @Operation(summary = "Read a registration (RFC 7592; the registration access token is replaced)")
    @GetMapping(value = "/oauth2/register/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> read(HttpServletRequest request, @PathVariable String clientId) {
        return noStore(ResponseEntity.ok()).body(registrations.read(clientId, bearer(request), base(target(request))));
    }

    @Operation(summary = "Replace a registration's metadata (RFC 7592; the registration access token is replaced)")
    @PutMapping(value = "/oauth2/register/{clientId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> update(HttpServletRequest request, @PathVariable String clientId,
            @RequestBody Map<String, Object> metadata) {
        return noStore(ResponseEntity.ok()).body(registrations.update(clientId, bearer(request), metadata, base(target(request))));
    }

    @Operation(summary = "Delete a registration (RFC 7592): the client stops working")
    @DeleteMapping("/oauth2/register/{clientId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String clientId) {
        registrations.delete(clientId, bearer(request));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ClientRegistrationService.RegistrationException.class)
    ResponseEntity<Map<String, Object>> refused(ClientRegistrationService.RegistrationException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.error());
        body.put("error_description", e.getMessage());
        ResponseEntity.BodyBuilder builder = noStore(ResponseEntity.status(e.status()));
        if (e.status() == 401) {
            builder.header("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        }
        return builder.body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException e) {
        // Checks shared with the admin API (redirect URIs, keys): reported as client metadata errors.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "invalid_client_metadata");
        body.put("error_description", e.getMessage());
        return noStore(ResponseEntity.badRequest()).body(body);
    }

    private static TenantIssuers.Target target(HttpServletRequest request) {
        return request.getAttribute(TenantIssuers.ATTRIBUTE) instanceof TenantIssuers.Target t ? t : null;
    }

    /** registration_client_uri lives under the issuer the request addressed. */
    private String base(TenantIssuers.Target target) {
        return (target == null ? issuers.sharedIssuer() : target.issuer()) + "/oauth2/register";
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder.header("Cache-Control", "no-store").header("Pragma", "no-cache");
    }
}
