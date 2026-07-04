package com.secureone.auth.mfa;

import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account MFA", description = "Self-service TOTP enrollment (session-authenticated)")
@RestController
@RequestMapping("/api/v1/account/mfa/totp")
public class MfaAccountController {

    public record EnrollRequest(String label) {}

    public record ConfirmRequest(@NotNull UUID factorId, @NotBlank String code) {}

    private final SessionUserResolver users;
    private final TotpMfaService totp;

    public MfaAccountController(SessionUserResolver users, TotpMfaService totp) {
        this.users = users;
        this.totp = totp;
    }

    @Operation(summary = "Begin TOTP enrollment")
    @PostMapping("/enroll")
    public Map<String, Object> enroll(Authentication authentication, @RequestBody(required = false) EnrollRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        TotpMfaService.EnrollResponse response = totp.enroll(user, body != null ? body.label() : null);
        return Map.of(
                "factorId", response.factorId().toString(),
                "secret", response.secret(),
                "otpauthUri", response.otpauthUri(),
                "qrCodeDataUrl", response.qrCodeDataUrl());
    }

    @Operation(summary = "Confirm TOTP enrollment")
    @PostMapping("/confirm")
    public Map<String, Object> confirm(
            Authentication authentication, @Valid @RequestBody ConfirmRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        TotpMfaService.ConfirmResponse response = totp.confirm(user, body.factorId(), body.code());
        return Map.of(
                "factorId", response.factorId().toString(),
                "recoveryCodes", response.recoveryCodes());
    }

    @Operation(summary = "Remove TOTP factor")
    @DeleteMapping("/{factorId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID factorId) {
        UserAccount user = users.requireTenantUser(authentication);
        totp.delete(user, factorId);
        return ResponseEntity.noContent().build();
    }
}
