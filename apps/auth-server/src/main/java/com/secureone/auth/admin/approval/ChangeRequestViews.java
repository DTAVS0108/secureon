package com.secureone.auth.admin.approval;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.approval.ChangeRequests.Row;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Who may decide a change request, and the request as the signed-in admin sees it. */
@Component
public class ChangeRequestViews {

    private final AdminAccessService access;

    public ChangeRequestViews(AdminAccessService access) {
        this.access = access;
    }

    public record ChangeRequestView(
            UUID id,
            String action,
            UUID tenantId,
            String tenantName,
            UUID applicationId,
            UUID targetId,
            String targetLabel,
            String reason,
            /** The change in words, e.g. "Weaker Two Factor Authentication for Shop: mode: required → off". */
            String details,
            String status,
            String requestedBy,
            Instant requestedAt,
            String decidedBy,
            Instant decidedAt,
            String decisionNote,
            /** The signed-in admin asked for this change (they may withdraw it, not decide it). */
            boolean mine,
            /** The signed-in admin may approve or reject it now. */
            boolean canDecide) {}

    /**
     * Platform operators, and tenant super admins of the request's tenant. Tenant and platform Two Factor
     * Authentication changes: platform operators only. Nobody decides an exemption of themselves.
     */
    public boolean isApprover(Row row, Authentication authentication, String actAs) {
        if (TwoFactorChangeRequests.USER_EXEMPT.equals(row.action())
                && row.targetLabel().equalsIgnoreCase(email(authentication, actAs))) {
            return false;
        }
        if (access.canAccessPlatformSettings(authentication, actAs)) {
            return true;
        }
        if (TwoFactorChangeRequests.PLATFORM_DECIDES.contains(row.action())) {
            return false;
        }
        return row.tenantId() != null && access.canManageTenantGovernance(authentication, actAs, row.tenantId());
    }

    public void requireApprover(Row row, Authentication authentication, String actAs) {
        if (!isApprover(row, authentication, actAs)) {
            throw new AccessDeniedException(TwoFactorChangeRequests.PLATFORM_DECIDES.contains(row.action())
                    ? "Only a platform operator can decide this request."
                    : "Only a platform operator or this tenant's super admin can decide this request (not one about yourself).");
        }
    }

    public String email(Authentication authentication, String actAs) {
        String email = access.resolveOperatorEmail(authentication, actAs);
        return email != null ? email : authentication.getName();
    }

    public ChangeRequestView view(Row row, Authentication authentication, String actAs) {
        boolean mine = row.requestedBy().equalsIgnoreCase(email(authentication, actAs));
        boolean canDecide = row.status() == ChangeRequests.Status.PENDING
                && !mine
                && isApprover(row, authentication, actAs);
        return new ChangeRequestView(
                row.id(), row.action(), row.tenantId(), row.tenantName(), row.applicationId(), row.targetId(),
                row.targetLabel(), row.reason(), row.details(), row.status().name(), row.requestedBy(), row.requestedAt(),
                row.decidedBy(), row.decidedAt(), row.decisionNote(), mine, canDecide);
    }
}
