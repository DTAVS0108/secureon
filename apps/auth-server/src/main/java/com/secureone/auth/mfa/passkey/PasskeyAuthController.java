package com.secureone.auth.mfa.passkey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public auth", description = "Passkey passwordless sign-in")
@RestController
@RequestMapping("/api/v1/auth/passkey")
public class PasskeyAuthController {

    public record OptionsRequest(String tenantSlug, String email, UUID applicationId) {}

    public record VerifyRequest(@NotNull UUID requestId, @NotBlank String credential, UUID applicationId) {}

    private final PasskeyAuthenticationService passkeys;

    public PasskeyAuthController(PasskeyAuthenticationService passkeys) {
        this.passkeys = passkeys;
    }

    @Operation(summary = "Begin passkey sign-in")
    @PostMapping("/options")
    public Map<String, Object> options(@RequestBody(required = false) OptionsRequest body) {
        String tenantSlug = body != null ? body.tenantSlug() : null;
        String email = body != null ? body.email() : null;
        UUID applicationId = body != null ? body.applicationId() : null;
        return passkeys.beginAuthentication(tenantSlug, email, applicationId);
    }

    @Operation(summary = "Complete passkey sign-in")
    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @Valid @RequestBody VerifyRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        return passkeys.finishAuthentication(
                body.requestId(), body.credential(), body.applicationId(), request, response);
    }
}
