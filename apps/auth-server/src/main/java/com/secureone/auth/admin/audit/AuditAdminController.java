package com.secureone.auth.admin.audit;

import com.secureone.auth.audit.AuditLog;
import com.secureone.auth.audit.AuditLogRepository;
import com.secureone.auth.util.JsonMaps;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.secureone.auth.admin.AdminListLimit;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — audit", description = "Security and admin action audit log")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/audit")
public class AuditAdminController {

    private final AuditLogRepository repository;

    public AuditAdminController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditEventResponse(
            UUID id,
            Instant timestamp,
            String actor,
            String action,
            String target,
            String ip,
            String result) {}

    @GetMapping
    public List<AuditEventResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID applicationId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer limit) {
        Pageable page = AdminListLimit.newest(limit);
        var rows =
                applicationId != null && tenantId != null
                        ? repository.findByApplicationIdAndTenantIdOrderByCreatedAtDesc(applicationId, tenantId, page)
                        : applicationId != null
                                ? repository.findByApplicationIdOrderByCreatedAtDesc(applicationId, page)
                                : repository.findRecent(page);
        return rows.stream().map(this::toResponse).toList();
    }

    private AuditEventResponse toResponse(AuditLog log) {
        Map<String, Object> meta = log.getMetadata() != null ? log.getMetadata() : Map.of();
        String actor = JsonMaps.stringVal(meta, "actorEmail", "system");
        String target = JsonMaps.stringVal(meta, "targetLabel", log.getTargetType() != null ? log.getTargetType() : "—");
        String result = JsonMaps.stringVal(meta, "result", "success");
        return new AuditEventResponse(
                log.getId(), log.getCreatedAt(), actor, log.getAction(), target, log.getIp(), result);
    }
}
