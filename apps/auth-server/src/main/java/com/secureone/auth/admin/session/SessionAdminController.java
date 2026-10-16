package com.secureone.auth.admin.session;

import com.secureone.auth.session.LoginHistory;
import com.secureone.auth.session.LoginHistoryRepository;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.util.JsonMaps;
import com.secureone.auth.admin.AdminListLimit;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — sessions", description = "Login history and active sessions")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/sessions")
public class SessionAdminController {

    private final LoginHistoryRepository repository;
    private final UserAccountRepository userRepository;

    public SessionAdminController(LoginHistoryRepository repository, UserAccountRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public record LoginEventResponse(
            UUID id,
            UUID userId,
            String userEmail,
            Instant timestamp,
            String ip,
            String location,
            String device,
            String method,
            String result,
            String sessionId) {}

    @GetMapping
    public List<LoginEventResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID userId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID applicationId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer limit) {
        Pageable page = AdminListLimit.newest(limit);
        var rows =
                userId != null
                        ? repository.findByUserIdOrderByCreatedAtDesc(userId, page)
                        : applicationId != null && tenantId != null
                                ? repository.findByApplicationIdAndTenantIdOrderByCreatedAtDesc(
                                        applicationId, tenantId, page)
                                : applicationId != null
                                        ? repository.findByApplicationIdOrderByCreatedAtDesc(applicationId, page)
                                        : repository.findRecent(page);
        // One lookup per distinct user, not per row.
        Map<UUID, String> emails = new HashMap<>();
        return rows.stream().map(row -> toResponse(row, emails)).toList();
    }

    private LoginEventResponse toResponse(LoginHistory row, Map<UUID, String> emails) {
        Map<String, Object> geo = row.getGeo() != null ? row.getGeo() : Map.of();
        String email = row.getUserId() != null
                ? emails.computeIfAbsent(row.getUserId(), id -> userRepository
                        .findById(id)
                        .map(u -> u.getEmail())
                        .orElse(id.toString()))
                : "unknown";
        String result = switch (row.getResult()) {
            case "SUCCESS" -> "success";
            case "FAILURE" -> "failure";
            case "MFA_REQUIRED" -> "mfa_required";
            default -> row.getResult().toLowerCase();
        };
        return new LoginEventResponse(
                row.getId(),
                row.getUserId(),
                email,
                row.getCreatedAt(),
                row.getIp(),
                JsonMaps.stringVal(geo, "location", "Unknown"),
                row.getDevice() != null ? row.getDevice() : "—",
                JsonMaps.stringVal(geo, "method", "password"),
                result,
                row.getSessionId());
    }
}
