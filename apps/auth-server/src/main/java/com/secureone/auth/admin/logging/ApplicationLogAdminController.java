package com.secureone.auth.admin.logging;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.logging.ApplicationLog;
import com.secureone.auth.logging.ApplicationLogFilter;
import com.secureone.auth.logging.ApplicationLogMaintenanceService;
import com.secureone.auth.logging.ApplicationLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Admin — application logs", description = "CloudWatch-style searchable application logs")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/logs")
public class ApplicationLogAdminController {

    private final ApplicationLogQueryService logs;
    private final ApplicationLogMaintenanceService maintenance;
    private final AdminAccessService access;

    public ApplicationLogAdminController(
            ApplicationLogQueryService logs,
            ApplicationLogMaintenanceService maintenance,
            AdminAccessService access) {
        this.logs = logs;
        this.maintenance = maintenance;
        this.access = access;
    }

    /**
     * Rows without an application or tenant are platform-wide (any tenant's sign-in attempts, startup,
     * background jobs). Only platform operators may see or delete them; for everyone else an
     * app-filtered query means exactly that app's rows.
     */
    private boolean unscopedAllowed(boolean requested, Authentication auth, String actAs) {
        return requested && access.canAccessPlatformSettings(auth, actAs);
    }

    public record ApplicationLogResponse(
            UUID id,
            Instant timestamp,
            String level,
            String logger,
            String message,
            String sessionId,
            String requestId,
            String principal,
            UUID tenantId,
            UUID applicationId,
            String ip,
            String userAgent,
            Map<String, Object> metadata) {}

    @Operation(
            summary = "Search application logs",
            description =
                    "Filter by session ID, request ID, level, time range, or message text. "
                            + "Use sessionId from console output or sign-in history.")
    @GetMapping
    public List<ApplicationLogResponse> search(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean includeUnscoped,
            @RequestParam(defaultValue = "200") int limit,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            Authentication auth) {
        includeUnscoped = unscopedAllowed(includeUnscoped, auth, actAs);
        int capped = Math.min(Math.max(limit, 1), 1000);
        return logs.search(
                        blankToNull(sessionId),
                        blankToNull(requestId),
                        blankToNull(level),
                        applicationId,
                        tenantId,
                        includeUnscoped,
                        since,
                        until,
                        blankToNull(search),
                        capped)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Operation(
            summary = "Delete application logs",
            description =
                    "Delete logs matching the same filters as search. Requires a date boundary: "
                            + "use period (today|yesterday), date (single day), or since/until (range). "
                            + "Optional session, level, and message filters narrow the delete.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> delete(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean includeUnscoped,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            Authentication auth) {
        includeUnscoped = unscopedAllowed(includeUnscoped, auth, actAs);
        ApplicationLogFilter filter = new ApplicationLogFilter(
                blankToNull(sessionId),
                blankToNull(requestId),
                blankToNull(level),
                applicationId,
                tenantId,
                includeUnscoped,
                since,
                until,
                blankToNull(search));
        if (filter.since() == null && filter.until() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide since/until (or period computed on the client) to bound the delete.");
        }
        long matching = maintenance.count(filter);
        int deleted = maintenance.delete(filter);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", deleted);
        out.put("matched", matching);
        return out;
    }

    private ApplicationLogResponse toResponse(ApplicationLog row) {
        return new ApplicationLogResponse(
                row.getId(),
                row.getCreatedAt(),
                row.getLevel(),
                row.getLogger(),
                row.getMessage(),
                row.getSessionId(),
                row.getRequestId(),
                row.getPrincipal(),
                row.getTenantId(),
                row.getApplicationId(),
                row.getIp(),
                row.getUserAgent(),
                row.getMetadata());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
