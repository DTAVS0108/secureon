package com.secureone.auth.admin.review;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.audit.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Periodic review of privileged access. Platform super admins review everyone (including platform
 * operators); tenant super admins review their own tenant. Completing a review is recorded in the
 * audit log as evidence.
 */
@Tag(name = "Admin — access review", description = "Who holds console and platform roles, with last use and MFA")
@RestController
public class AccessReviewController {

    private final AccessReviewService reviews;
    private final AdminAccessService access;
    private final AuditService audit;

    public AccessReviewController(AccessReviewService reviews, AdminAccessService access, AuditService audit) {
        this.reviews = reviews;
        this.access = access;
        this.audit = audit;
    }

    public record CompleteReviewRequest(String note, Integer entriesReviewed) {}

    @GetMapping("/api/admin/v1/access-review")
    public List<AccessReviewService.Entry> platformReview(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return reviews.review(null);
    }

    @GetMapping("/api/admin/v1/tenants/{tenantId}/access-review")
    public List<AccessReviewService.Entry> tenantReview(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAsEmail, tenantId);
        return reviews.review(tenantId);
    }

    @PostMapping("/api/admin/v1/access-review/complete")
    public Map<String, Object> completePlatformReview(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody(required = false) CompleteReviewRequest body) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return complete(authentication, null, body);
    }

    @PostMapping("/api/admin/v1/tenants/{tenantId}/access-review/complete")
    public Map<String, Object> completeTenantReview(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @RequestBody(required = false) CompleteReviewRequest body) {
        access.requireTenantGovernanceAccess(authentication, actAsEmail, tenantId);
        return complete(authentication, tenantId, body);
    }

    private Map<String, Object> complete(Authentication authentication, UUID tenantId, CompleteReviewRequest body) {
        int count = body != null && body.entriesReviewed() != null
                ? body.entriesReviewed()
                : reviews.review(tenantId).size();
        String note = body != null && body.note() != null ? body.note().trim() : "";
        if (note.length() > 500) {
            note = note.substring(0, 500);
        }
        String label = count + " entries reviewed" + (note.isEmpty() ? "" : ": " + note);
        audit.record(tenantId, authentication.getName(), "access_review.completed", "access_review", null, label, true);
        return Map.of("ok", true, "entriesReviewed", count);
    }
}
