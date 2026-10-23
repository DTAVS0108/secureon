package com.secureone.auth.admin.approval;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.approval.ChangeRequests.Row;
import com.secureone.auth.admin.approval.ChangeRequests.Status;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminService;
import com.secureone.auth.audit.AuditService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ChangeRequestServiceTest {

    private static final String REQUESTER = "alice@example.com";
    private static final String APPROVER = "bob@example.com";

    private final ChangeRequests requests = mock(ChangeRequests.class);
    private final OAuthClientAdminService clients = mock(OAuthClientAdminService.class);
    private final AuditService audit = mock(AuditService.class);
    private final TwoFactorChangeRequests twoFactor = mock(TwoFactorChangeRequests.class);
    private ChangeRequestService service;

    private final UUID requestId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org.springframework.beans.factory.ObjectProvider<TwoFactorChangeRequests> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(twoFactor);
        service = new ChangeRequestService(requests, clients, audit, provider);
    }

    private Row row(Status status) {
        return new Row(requestId, ChangeRequestService.OAUTH_CLIENT_DELETE, UUID.randomUUID(), UUID.randomUUID(),
                clientId, "web-client (Shop)", null, status, REQUESTER, Instant.now(), null, null, null, "Acme",
                null, null);
    }

    private Row twoFactorRow() {
        UUID app = UUID.randomUUID();
        return new Row(requestId, TwoFactorChangeRequests.APP_OFF, UUID.randomUUID(), app, app, "Shop", null,
                Status.PENDING, REQUESTER, Instant.now(), null, null, null, "Acme",
                "{\"before\":{\"tabEnabled\":true}}", "Turn Two Factor Authentication off for Shop");
    }

    @Test
    void approvingATwoFactorChangeRunsIt() {
        Row pending = twoFactorRow();
        when(requests.find(requestId)).thenReturn(Optional.of(pending));
        when(twoFactor.apply(pending)).thenReturn(null);
        when(requests.decide(requestId, Status.APPROVED, APPROVER, "ok")).thenReturn(true);

        service.approve(requestId, APPROVER, "ok");

        verify(twoFactor).apply(pending);
        verify(requests).decide(requestId, Status.APPROVED, APPROVER, "ok");
        verify(clients, never()).delete(any());
    }

    @Test
    void aStaleTwoFactorChangeFailsWithTheReason() {
        Row pending = twoFactorRow();
        when(requests.find(requestId)).thenReturn(Optional.of(pending));
        when(twoFactor.apply(pending)).thenReturn("The setting changed after this request was made");
        when(requests.decide(requestId, Status.FAILED, APPROVER, "The setting changed after this request was made"))
                .thenReturn(true);

        service.approve(requestId, APPROVER, "ok");

        verify(requests).decide(requestId, Status.FAILED, APPROVER, "The setting changed after this request was made");
    }

    private OAuthClientResponse client() {
        return new OAuthClientResponse(clientId, UUID.randomUUID(), UUID.randomUUID(), "Shop", "web-client",
                "web", "active", null, null, null, null, true, true, null, true, null, null, null, null, null);
    }

    @Test
    void askingTwiceReturnsThePendingRequest() {
        Row pending = row(Status.PENDING);
        when(requests.findPending(ChangeRequestService.OAUTH_CLIENT_DELETE, clientId)).thenReturn(Optional.of(pending));

        assertSame(pending, service.requestClientDelete(client(), REQUESTER, "no longer used"));
        verify(requests, never()).create(anyString(), any(), any(), any(), anyString(), any(), anyString());
    }

    @Test
    void requesterCannotApproveOrRejectTheirOwnRequest() {
        when(requests.find(requestId)).thenReturn(Optional.of(row(Status.PENDING)));

        assertThrows(AccessDeniedException.class, () -> service.approve(requestId, "ALICE@example.com", null));
        assertThrows(AccessDeniedException.class, () -> service.reject(requestId, REQUESTER, "no"));
        verify(clients, never()).delete(any());
    }

    @Test
    void approvalDeletesTheClient() {
        when(requests.find(requestId)).thenReturn(Optional.of(row(Status.PENDING)));
        when(requests.decide(requestId, Status.APPROVED, APPROVER, "ok")).thenReturn(true);

        service.approve(requestId, APPROVER, "ok");

        verify(clients).delete(clientId);
        verify(requests).decide(requestId, Status.APPROVED, APPROVER, "ok");
    }

    @Test
    void approvalOfAClientThatIsGoneIsMarkedFailed() {
        when(requests.find(requestId)).thenReturn(Optional.of(row(Status.PENDING)));
        when(clients.get(clientId)).thenThrow(new ResourceNotFoundException("gone"));
        when(requests.decide(eq(requestId), eq(Status.FAILED), eq(APPROVER), anyString())).thenReturn(true);

        service.approve(requestId, APPROVER, null);

        verify(clients, never()).delete(any());
        verify(requests).decide(eq(requestId), eq(Status.FAILED), eq(APPROVER), anyString());
    }

    @Test
    void decidedRequestsCannotBeDecidedAgain() {
        when(requests.find(requestId)).thenReturn(Optional.of(row(Status.REJECTED)));

        assertThrows(ConflictException.class, () -> service.approve(requestId, APPROVER, null));
        verify(clients, never()).delete(any());
    }

    @Test
    void rejectionNeedsAReason() {
        when(requests.find(requestId)).thenReturn(Optional.of(row(Status.PENDING)));

        assertThrows(IllegalArgumentException.class, () -> service.reject(requestId, APPROVER, "  "));
    }

    @Test
    void onlyTheRequesterCanWithdraw() {
        when(requests.find(requestId)).thenReturn(Optional.of(row(Status.PENDING)));
        when(requests.decide(requestId, Status.WITHDRAWN, REQUESTER, null)).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> service.withdraw(requestId, APPROVER));
        service.withdraw(requestId, REQUESTER);
        verify(requests).decide(requestId, Status.WITHDRAWN, REQUESTER, null);
    }
}
