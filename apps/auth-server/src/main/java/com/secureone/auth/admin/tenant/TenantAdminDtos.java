package com.secureone.auth.admin.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TenantAdminDtos {

    private TenantAdminDtos() {}

    public record TenantResponse(
            UUID id,
            String name,
            String slug,
            String status,
            String plan,
            long userCount,
            long appCount,
            Instant createdAt,
            /** The reserved platform tenant: cannot be deleted, renamed (slug) or suspended. */
            boolean system) {}

    public record TenantCreateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String slug,
            @Pattern(regexp = "free|team|enterprise", message = "plan must be free, team, or enterprise") String plan,
            @Pattern(regexp = "ACTIVE|SUSPENDED|active|suspended", message = "status must be ACTIVE or SUSPENDED")
                    String status) {}

    public record TenantUpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String slug,
            @Pattern(regexp = "free|team|enterprise", message = "plan must be free, team, or enterprise") String plan,
            @Pattern(regexp = "ACTIVE|SUSPENDED|active|suspended", message = "status must be ACTIVE or SUSPENDED")
                    String status) {}
}
