package com.secureone.auth.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ApplicationAccountDtos {

    private ApplicationAccountDtos() {}

    public record EmailRequest(@NotBlank @Email String email) {}

    public record ForgotPasswordRequest(String email, String identifier, String mode, String channel) {
        public String resolveIdentifier() {
            if (identifier != null && !identifier.isBlank()) {
                return identifier.trim();
            }
            return email != null ? email.trim() : null;
        }
    }

    public record ResetPasswordOtpRequest(
            @NotBlank String challengeId,
            @NotBlank @Size(min = 4, max = 8) String code,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record SessionLoginRequest(
            @NotBlank @Size(min = 1, max = 255) String identifier,
            @NotBlank @Size(min = 1, max = 128) String password) {}

    public record OtpLoginRequest(@NotBlank @Size(min = 1, max = 255) String identifier, String channel) {}

    public record OtpVerifyRequest(
            @NotBlank String challengeId,
            @NotBlank @Size(min = 4, max = 8) String code,
            String channel) {}
}
