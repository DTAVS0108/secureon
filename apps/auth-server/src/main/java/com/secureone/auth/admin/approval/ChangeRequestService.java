package com.secureone.auth.admin.approval;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.approval.ChangeRequests.Row;
import com.secureone.auth.admin.approval.ChangeRequests.Status;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminService;
import com.secureone.auth.audit.AuditService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * High-impact changes that wait for a second admin (docs/plans/change-approvals.md). For now one action:
 * deleting an OAuth client. Callers check who may request, list and decide; this class enforces the
 * request rules: one pending request per target, nobody decides their own request.
 */
@Service
public class ChangeRequestService {

    public static final String OAUTH_CLIENT_DELETE = "oauth_client.delete";

    private static final int HISTORY_LIMIT = 50;
    private static final int NOTE_MAX = 500;

    private final ChangeRequests requests;
    private final OAuthClientAdminService clients;
    private final AuditService audit;

    public ChangeRequestService(ChangeRequests requests, OAuthClientAdminService clients, AuditService audit) {
        this.requests = requests;
        this.clients = clients;
        this.audit = audit;
    }

    /** Asks for {@code client} to be deleted; returns the pending request (an existing one when already asked). */
    @Transactional
    public Row requestClientDelete(OAuthClientResponse client, String requester, String reason) {
        Optional<Row> existing = requests.findPending(OAUTH_CLIENT_DELETE, client.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        Row row = requests.create(
                OAUTH_CLIENT_DELETE,
                client.tenantId(),
                client.applicationId(),
                client.id(),
                label(client),
                trim(reason),
                requester);
        audit.record(client.tenantId(), client.applicationId(), requester, "change_request.requested",
                "oauth_client", client.id(), "Delete " + row.targetLabel(), true);
        return row;
    }

    public Optional<Row> pendingClientDelete(UUID clientId) {
        return requests.findPending(OAUTH_CLIENT_DELETE, clientId);
    }

    public Row get(UUID id) {
        return requests.find(id).orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + id));
    }

    public List<Row> list(UUID tenantId) {
        return requests.list(tenantId, HISTORY_LIMIT);
    }

    /** Applies the change, then records the approval. The approver's rights are checked by the caller. */
    @Transactional
    public Row approve(UUID id, String approver, String note) {
        Row row = requirePending(id);
        requireNotOwn(row, approver);
        boolean applied = apply(row);
        Status status = applied ? Status.APPROVED : Status.FAILED;
        String decisionNote = applied ? trim(note) : "The OAuth client no longer exists.";
        if (!requests.decide(id, status, approver, decisionNote)) {
            throw new ConflictException("This request was already decided by someone else.");
        }
        audit.record(row.tenantId(), null, approver,
                applied ? "change_request.approved" : "change_request.failed",
                "oauth_client", row.targetId(), "Delete " + row.targetLabel() + " (requested by " + row.requestedBy() + ")",
                applied);
        return get(id);
    }

    @Transactional
    public Row reject(UUID id, String approver, String note) {
        Row row = requirePending(id);
        requireNotOwn(row, approver);
        String reason = trim(note);
        if (reason == null) {
            throw new IllegalArgumentException("Say why the request is rejected.");
        }
        decideOrConflict(id, Status.REJECTED, approver, reason);
        audit.record(row.tenantId(), null, approver, "change_request.rejected",
                "oauth_client", row.targetId(), "Delete " + row.targetLabel() + ": " + reason, true);
        return get(id);
    }

    /** The requester takes their own request back. */
    @Transactional
    public Row withdraw(UUID id, String requester) {
        Row row = requirePending(id);
        if (!row.requestedBy().equalsIgnoreCase(requester)) {
            throw new AccessDeniedException("Only the person who asked can withdraw this request.");
        }
        decideOrConflict(id, Status.WITHDRAWN, requester, null);
        audit.record(row.tenantId(), null, requester, "change_request.withdrawn",
                "oauth_client", row.targetId(), "Delete " + row.targetLabel(), true);
        return get(id);
    }

    private boolean apply(Row row) {
        if (!OAUTH_CLIENT_DELETE.equals(row.action())) {
            throw new IllegalStateException("Unknown change: " + row.action());
        }
        try {
            clients.get(row.targetId());
        } catch (ResourceNotFoundException gone) {
            return false;
        }
        clients.delete(row.targetId());
        return true;
    }

    private Row requirePending(UUID id) {
        Row row = get(id);
        if (row.status() != Status.PENDING) {
            throw new ConflictException("This request is already " + row.status().name().toLowerCase() + ".");
        }
        return row;
    }

    private static void requireNotOwn(Row row, String decider) {
        if (row.requestedBy().equalsIgnoreCase(decider)) {
            throw new AccessDeniedException(
                    "You asked for this change, so another admin must approve or reject it. You can withdraw it.");
        }
    }

    private void decideOrConflict(UUID id, Status status, String by, String note) {
        if (!requests.decide(id, status, by, note)) {
            throw new ConflictException("This request was already decided by someone else.");
        }
    }

    private static String label(OAuthClientResponse client) {
        String app = client.applicationName();
        String label = app == null || app.isBlank() ? client.clientId() : client.clientId() + " (" + app + ")";
        return label.length() > 255 ? label.substring(0, 255) : label;
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() > NOTE_MAX ? t.substring(0, NOTE_MAX) : t;
    }
}
