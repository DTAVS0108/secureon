package com.secureone.auth.iga;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.webhook.WebhookEventPublisher;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AccessRequestService {

    private final AccessRequestRepository requests;
    private final AccessRequestGrantService grantService;
    private final ObjectProvider<WebhookEventPublisher> webhookPublisher;
    private final com.secureone.auth.user.UserAccountRepository users;

    /** Resource types the grant step understands (see AccessRequestGrantService). */
    static final java.util.Set<String> RESOURCE_TYPES = java.util.Set.of("role", "tenant_role", "application_role", "app_role");

    public AccessRequestService(
            AccessRequestRepository requests,
            AccessRequestGrantService grantService,
            ObjectProvider<WebhookEventPublisher> webhookPublisher,
            com.secureone.auth.user.UserAccountRepository users) {
        this.requests = requests;
        this.grantService = grantService;
        this.webhookPublisher = webhookPublisher;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return requests.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Map<String, Object> create(UUID tenantId, Map<String, Object> body) {
        AccessRequest request = new AccessRequest();
        request.setTenantId(tenantId);
        request.setRequesterUserId(requireTenantMember(tenantId, requiredUuid(body.get("requesterUserId"), "requesterUserId")));
        UUID targetUserId = parseUuid(body.get("targetUserId"));
        request.setTargetUserId(targetUserId != null ? requireTenantMember(tenantId, targetUserId) : null);
        String resourceType = requiredString(body.get("resourceType"), "resourceType").toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        if (!RESOURCE_TYPES.contains(resourceType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "resourceType must be one of " + RESOURCE_TYPES.stream().sorted().toList() + ".");
        }
        request.setResourceType(resourceType);
        request.setResourceId(requiredString(body.get("resourceId"), "resourceId"));
        request.setApplicationId(parseUuid(body.get("applicationId")));
        request.setReason(string(body.get("reason")));
        request.setStatus("PENDING");
        AccessRequest saved = requests.save(request);
        publishAccessRequestEvent(saved, "access_request.created");
        return toResponse(saved);
    }

    public Map<String, Object> approve(UUID tenantId, UUID id, UUID reviewerId) {
        AccessRequest request = requirePending(tenantId, id);
        assertNotSelfReview(request, reviewerId);
        Map<String, Object> grant = grantService.applyGrant(request, reviewerId);
        request.setStatus("APPROVED");
        request.setReviewerId(reviewerId);
        request.setResolvedAt(Instant.now());
        if (Boolean.TRUE.equals(grant.get("grantApplied"))) {
            request.setGrantType(string(grant.get("grantType")));
            request.setGrantedRoleId(parseUuid(grant.get("grantedRoleId")));
            request.setGrantedUserId(parseUuid(grant.get("grantedUserId")));
            request.setGrantedRoleName(string(grant.get("grantedRoleName")));
        }
        AccessRequest saved = requests.save(request);
        Map<String, Object> response = toResponse(saved);
        response.putAll(grant);
        publishAccessRequestEvent(saved, "access_request.approved", grant);
        return response;
    }

    public Map<String, Object> deny(UUID tenantId, UUID id, UUID reviewerId) {
        AccessRequest request = requirePending(tenantId, id);
        assertNotSelfReview(request, reviewerId);
        request.setStatus("DENIED");
        request.setReviewerId(reviewerId);
        request.setResolvedAt(Instant.now());
        AccessRequest saved = requests.save(request);
        publishAccessRequestEvent(saved, "access_request.denied");
        return toResponse(saved);
    }

    /** Segregation of duties: nobody reviews a request they made or that grants them access. */
    private static void assertNotSelfReview(AccessRequest request, UUID reviewerId) {
        if (reviewerId != null
                && (reviewerId.equals(request.getRequesterUserId()) || reviewerId.equals(request.getTargetUserId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot review your own access request.");
        }
    }

    private UUID requireTenantMember(UUID tenantId, UUID userId) {
        users.findById(userId)
                .filter(user -> tenantId.equals(user.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User " + userId + " is not a member of this tenant."));
        return userId;
    }

    private void publishAccessRequestEvent(AccessRequest request, String eventType) {
        publishAccessRequestEvent(request, eventType, Map.of());
    }

    private void publishAccessRequestEvent(AccessRequest request, String eventType, Map<String, Object> extra) {
        webhookPublisher.ifAvailable(publisher -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getId().toString());
            payload.put("tenantId", request.getTenantId().toString());
            payload.put("requesterUserId", request.getRequesterUserId().toString());
            payload.put("targetUserId", request.getTargetUserId() != null ? request.getTargetUserId().toString() : null);
            payload.put("resourceType", request.getResourceType());
            payload.put("resourceId", request.getResourceId());
            payload.put("status", request.getStatus());
            payload.put("reviewerId", request.getReviewerId() != null ? request.getReviewerId().toString() : null);
            if (request.getApplicationId() != null) {
                payload.put("applicationId", request.getApplicationId().toString());
            }
            payload.putAll(extra);
            publisher.publish(request.getTenantId(), request.getApplicationId(), eventType, payload);
        });
    }

    private AccessRequest requirePending(UUID tenantId, UUID id) {
        AccessRequest request = requests.findById(id)
                .filter(row -> tenantId.equals(row.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found: " + id));
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Access request is already resolved.");
        }
        return request;
    }

    private Map<String, Object> toResponse(AccessRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", request.getId().toString());
        out.put("tenantId", request.getTenantId().toString());
        out.put("requesterUserId", request.getRequesterUserId().toString());
        out.put("targetUserId", request.getTargetUserId() != null ? request.getTargetUserId().toString() : null);
        out.put("resourceType", request.getResourceType());
        out.put("resourceId", request.getResourceId());
        out.put("applicationId", request.getApplicationId() != null ? request.getApplicationId().toString() : null);
        out.put("reason", request.getReason());
        out.put("status", request.getStatus());
        out.put("reviewerId", request.getReviewerId() != null ? request.getReviewerId().toString() : null);
        out.put("createdAt", request.getCreatedAt().toString());
        out.put("resolvedAt", request.getResolvedAt() != null ? request.getResolvedAt().toString() : null);
        out.put("grantApplied", request.getGrantType() != null);
        out.put("grantType", request.getGrantType());
        out.put("grantedRoleId", request.getGrantedRoleId() != null ? request.getGrantedRoleId().toString() : null);
        out.put("grantedRoleName", request.getGrantedRoleName());
        out.put("grantedUserId", request.getGrantedUserId() != null ? request.getGrantedUserId().toString() : null);
        return out;
    }

    private static UUID requiredUuid(Object raw, String field) {
        UUID value = parseUuid(raw);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value;
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID: " + raw);
        }
    }

    private static String requiredString(Object raw, String field) {
        String value = string(raw);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value;
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString().trim() : null;
    }
}
