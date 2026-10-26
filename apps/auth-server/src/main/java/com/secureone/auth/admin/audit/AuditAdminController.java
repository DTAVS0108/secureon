package com.secureone.auth.admin.audit;

import com.secureone.auth.admin.AdminListLimit;
import com.secureone.auth.audit.AuditService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — audit", description = "Security and admin action audit log")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/audit")
public class AuditAdminController {

    private final AuditQueryService queries;
    private final AuditService audit;

    public AuditAdminController(AuditQueryService queries, AuditService audit) {
        this.queries = queries;
        this.audit = audit;
    }

    /**
     * Newest first. Without {@code applicationId} this covers every tenant (platform super admins only, checked by
     * the admin interceptor).
     */
    @GetMapping
    public List<AuditQueryService.Row> list(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Integer limit) {
        int max = AdminListLimit.newest(limit).getPageSize();
        return queries.search(new AuditQueryService.Filter(applicationId, tenantId, from, to, action, actor, result), max);
    }

    /** The same filters as a CSV file (up to 100,000 rows). The export itself is audited. */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String result,
            HttpServletResponse response) throws IOException {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC).format(Instant.now());
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"secureone-audit-" + stamp + ".csv\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        audit.record(tenantId, applicationId, "admin", "audit.exported", "audit_log", null,
                applicationId != null ? "application" : tenantId != null ? "tenant" : "platform", true);
        queries.exportCsv(new AuditQueryService.Filter(applicationId, tenantId, from, to, action, actor, result),
                response.getWriter());
    }
}
