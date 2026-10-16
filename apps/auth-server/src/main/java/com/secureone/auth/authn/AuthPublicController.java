package com.secureone.auth.authn;

import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.platform.AuthSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Public auth", description = "Authentication method catalog for login UIs")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthPublicController {

    private final AuthSettingsService authSettings;
    private final ApplicationEffectiveSettingsService effectiveSettings;

    public AuthPublicController(
            AuthSettingsService authSettings, ApplicationEffectiveSettingsService effectiveSettings) {
        this.authSettings = authSettings;
        this.effectiveSettings = effectiveSettings;
    }

    /**
     * Sign-in methods for a login page. With {@code applicationId}, availability is the application's
     * effective setting (what sign-in will actually accept), not the platform default.
     */
    @GetMapping("/methods")
    public List<Map<String, Object>> methods(@RequestParam(required = false) UUID applicationId) {
        return authSettings.getAuthMethods().stream()
                .map(m -> {
                    String id = String.valueOf(m.get("id"));
                    boolean implemented = !Boolean.FALSE.equals(m.get("implemented"));
                    boolean enabled = applicationId != null
                            ? effectiveSettings.isMethodAvailable(applicationId, id)
                            : Boolean.TRUE.equals(m.get("enabled"));
                    return Map.<String, Object>of(
                            "id", id,
                            "name", m.get("name"),
                            "description", m.get("description"),
                            "category", m.get("category"),
                            "enabled", enabled,
                            "implemented", implemented,
                            "available", enabled && implemented);
                })
                .toList();
    }

    @Operation(
            summary = "Current browser session",
            description =
                    "Returns the signed-in user after form login (session cookie). "
                            + "Does not issue OAuth access tokens — use the authorization code + PKCE flow for those.")
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
        }
        String principal = authentication.getName();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authenticated", true);
        out.put("principal", principal);
        out.put(
                "authorities",
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        int sep = principal.indexOf(':');
        if (sep > 0) {
            out.put("tenantSlug", principal.substring(0, sep).trim().toLowerCase(Locale.ROOT));
            out.put("email", principal.substring(sep + 1).trim().toLowerCase(Locale.ROOT));
            out.put("authType", "tenant_user");
        } else {
            out.put("tenantSlug", null);
            out.put("email", null);
            out.put("authType", "platform_admin");
        }
        out.put(
                "oauth",
                Map.of(
                        "authorize", "/oauth2/authorize",
                        "token", "/oauth2/token",
                        "userinfo", "/userinfo",
                        "discovery", "/.well-known/openid-configuration"));
        return out;
    }
}
