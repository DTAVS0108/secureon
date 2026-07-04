package com.secureone.auth.authn;

import com.secureone.auth.application.ApplicationTenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-based login for integrated apps that render their own sign-in UI. Establishes a browser
 * session cookie on the auth-server origin; the app then redirects to {@code /oauth2/authorize} to
 * complete PKCE and receive access + refresh tokens.
 */
@Tag(name = "Public auth", description = "Authentication method catalog and session login for native UIs")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    private final AuthSessionService sessions;
    private final ApplicationTenantResolver applicationTenants;

    public AuthSessionController(AuthSessionService sessions, ApplicationTenantResolver applicationTenants) {
        this.sessions = sessions;
        this.applicationTenants = applicationTenants;
    }

    /**
     * @deprecated Prefer {@code POST /api/v1/applications/{applicationId}/auth/session/login} with
     *     {@code { email, password }} only.
     */
    @Deprecated
    @Operation(
            summary = "Session login (native app UI)",
            description =
                    "Validates credentials and creates an auth-server session cookie. "
                            + "Prefer the application-scoped endpoint when integrating a single app.")
    @PostMapping("/session/login")
    public ResponseEntity<?> sessionLogin(
            @Valid @RequestBody AuthSessionDtos.SessionLoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (body.applicationId() != null) {
            String tenantSlug = applicationTenants.requireTenantSlug(body.applicationId());
            return sessions.sessionLogin(
                    tenantSlug, body.email(), body.password(), body.applicationId(), request, response);
        }
        if (body.tenantSlug() == null || body.tenantSlug().isBlank()) {
            throw new IllegalArgumentException("applicationId or tenantSlug is required");
        }
        return sessions.sessionLogin(body.tenantSlug(), body.email(), body.password(), request, response);
    }
}
