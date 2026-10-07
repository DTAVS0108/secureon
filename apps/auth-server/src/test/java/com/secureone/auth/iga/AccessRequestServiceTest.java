package com.secureone.auth.iga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.webhook.WebhookEventPublisher;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccessRequestServiceTest {

    @Mock
    private AccessRequestRepository requests;

    @Mock
    private AccessRequestGrantService grantService;

    @Mock
    private ObjectProvider<WebhookEventPublisher> webhookPublisher;

    @Mock
    private com.secureone.auth.user.UserAccountRepository users;

    private AccessRequestService service;

    @BeforeEach
    void setUp() {
        service = new AccessRequestService(requests, grantService, webhookPublisher, users);
    }

    @Test
    void createPersistsPendingRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        com.secureone.auth.user.UserAccount requester = new com.secureone.auth.user.UserAccount();
        requester.setId(requesterId);
        requester.setTenantId(tenantId);
        when(users.findById(requesterId)).thenReturn(java.util.Optional.of(requester));
        when(requests.save(any(AccessRequest.class))).thenAnswer(invocation -> {
            AccessRequest row = invocation.getArgument(0);
            row.setId(UUID.randomUUID());
            if (row.getCreatedAt() == null) {
                row.setCreatedAt(java.time.Instant.now());
            }
            return row;
        });

        Map<String, Object> created = service.create(
                tenantId,
                Map.of(
                        "requesterUserId", requesterId.toString(),
                        "resourceType", "role",
                        "resourceId", "admin",
                        "reason", "Need admin access"));

        assertEquals("PENDING", created.get("status"));
        ArgumentCaptor<AccessRequest> captor = ArgumentCaptor.forClass(AccessRequest.class);
        verify(requests).save(captor.capture());
        assertEquals(tenantId, captor.getValue().getTenantId());
        assertEquals(requesterId, captor.getValue().getRequesterUserId());
    }

    @Test
    void approveMarksRequestApprovedAndMergesGrantMetadata() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        AccessRequest request = new AccessRequest();
        request.setId(requestId);
        request.setTenantId(tenantId);
        request.setRequesterUserId(UUID.randomUUID());
        request.setResourceType("role");
        request.setResourceId("admin");
        request.setStatus("PENDING");
        request.setCreatedAt(java.time.Instant.now());

        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(grantService.applyGrant(request, reviewerId))
                .thenReturn(Map.of("grantApplied", true, "grantType", "tenant_role"));
        when(requests.save(request)).thenReturn(request);

        Map<String, Object> approved = service.approve(tenantId, requestId, reviewerId);

        assertEquals("APPROVED", approved.get("status"));
        assertEquals(reviewerId.toString(), approved.get("reviewerId"));
        assertEquals(true, approved.get("grantApplied"));
        assertEquals("tenant_role", approved.get("grantType"));
        verify(grantService).applyGrant(request, reviewerId);
    }

    @Test
    void denyMarksRequestDeniedWithoutGrant() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        AccessRequest request = new AccessRequest();
        request.setId(requestId);
        request.setTenantId(tenantId);
        request.setRequesterUserId(UUID.randomUUID());
        request.setResourceType("role");
        request.setResourceId("admin");
        request.setStatus("PENDING");
        request.setCreatedAt(java.time.Instant.now());

        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(requests.save(request)).thenReturn(request);

        Map<String, Object> denied = service.deny(tenantId, requestId, reviewerId);

        assertEquals("DENIED", denied.get("status"));
    }

    @Test
    void approveRejectsAlreadyResolvedRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AccessRequest request = new AccessRequest();
        request.setId(requestId);
        request.setTenantId(tenantId);
        request.setStatus("APPROVED");

        when(requests.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(ResponseStatusException.class, () -> service.approve(tenantId, requestId, UUID.randomUUID()));
    }

    @Test
    void approveThrowsWhenTenantMismatch() {
        UUID requestId = UUID.randomUUID();
        AccessRequest request = new AccessRequest();
        request.setId(requestId);
        request.setTenantId(UUID.randomUUID());
        request.setStatus("PENDING");

        when(requests.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(ResourceNotFoundException.class, () -> service.approve(UUID.randomUUID(), requestId, UUID.randomUUID()));
    }

    @Test
    void createRejectsUserFromAnotherTenant() {
        UUID requesterId = UUID.randomUUID();
        com.secureone.auth.user.UserAccount other = new com.secureone.auth.user.UserAccount();
        other.setId(requesterId);
        other.setTenantId(UUID.randomUUID());
        when(users.findById(requesterId)).thenReturn(java.util.Optional.of(other));

        assertThrows(ResponseStatusException.class, () -> service.create(UUID.randomUUID(), Map.of(
                "requesterUserId", requesterId.toString(), "resourceType", "role", "resourceId", "admin")));
    }

    @Test
    void createRejectsUnknownResourceType() {
        UUID tenantId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        com.secureone.auth.user.UserAccount requester = new com.secureone.auth.user.UserAccount();
        requester.setId(requesterId);
        requester.setTenantId(tenantId);
        when(users.findById(requesterId)).thenReturn(java.util.Optional.of(requester));

        assertThrows(ResponseStatusException.class, () -> service.create(tenantId, Map.of(
                "requesterUserId", requesterId.toString(), "resourceType", "bogus", "resourceId", "x")));
    }
}
