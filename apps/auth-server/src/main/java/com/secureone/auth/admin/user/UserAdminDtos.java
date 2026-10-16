package com.secureone.auth.admin.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UserAdminDtos {

    private UserAdminDtos() {}

    public record MfaFactorResponse(String id, String type, String label, boolean verified) {}

    public record UserResponse(
            UUID id,
            UUID tenantId,
            String email,
            String username,
            String firstName,
            String lastName,
            String status,
            boolean emailVerified,
            boolean hasPassword,
            boolean locked,
            int failedLoginCount,
            List<String> roleIds,
            List<String> roleNames,
            List<MfaFactorResponse> mfaFactors,
            Map<String, Boolean> allowedAuthMethods,
            Instant lastLoginAt,
            Instant createdAt,
            String phone,
            boolean phoneVerified) {}

    public record UserAuthMethodsUpdateRequest(Map<String, Boolean> methods) {}

    public record UserCreateRequest(
            UUID tenantId,
            @NotBlank @Email String email,
            @Size(max = 150) String username,
            @Size(max = 150) String firstName,
            @Size(max = 150) String lastName,
            @Size(max = 32) String status,
            List<UUID> roleIds,
            UUID applicationId) {}

    public record AdminSetPasswordRequest(@NotBlank @Size(min = 8, max = 128) String password) {}

    public record UserUpdateRequest(
            @NotBlank @Email String email,
            @Size(max = 150) String username,
            @Size(max = 150) String firstName,
            @Size(max = 150) String lastName,
            @Size(max = 32) String status,
            @JsonProperty("emailVerified") Boolean emailVerified,
            List<UUID> roleIds) {}
}
