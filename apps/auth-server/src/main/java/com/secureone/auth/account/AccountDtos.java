package com.secureone.auth.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {}

    /** @deprecated Prefer application-scoped {@code POST /api/v1/applications/{applicationId}/account/...}. */
    @Deprecated
    public record TenantEmailRequest(String tenantSlug, java.util.UUID applicationId, @NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String password,
            java.util.UUID applicationId) {}

    public record ResetPasswordOtpRequest(
            @NotBlank String challengeId,
            @NotBlank @Size(min = 4, max = 8) String code,
            @NotBlank @Size(min = 8, max = 128) String password,
            java.util.UUID applicationId) {}
}
