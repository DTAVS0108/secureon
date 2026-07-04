package com.secureone.auth.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountSessionServiceTest {

    @Mock
    private FindByIndexNameSessionRepository<Session> sessions;

    @Mock
    private Session sessionA;

    @Mock
    private Session sessionB;

    private AccountSessionService service;

    @BeforeEach
    void setUp() {
        service = new AccountSessionService(sessions);
    }

    @Test
    void listsSessionsForPrincipal() {
        when(sessionA.getCreationTime()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(sessionA.getLastAccessedTime()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));
        when(sessionB.getCreationTime()).thenReturn(Instant.parse("2026-01-01T01:00:00Z"));
        when(sessionB.getLastAccessedTime()).thenReturn(Instant.parse("2026-01-03T00:00:00Z"));
        when(sessions.findByPrincipalName("acme:user@acme.com"))
                .thenReturn(Map.of("sess-a", sessionA, "sess-b", sessionB));

        var views = service.list("acme:user@acme.com", "sess-a");

        assertEquals(2, views.size());
        assertEquals("sess-b", views.get(0).id());
        assertEquals(true, views.get(1).current());
    }

    @Test
    void revokeOthersSkipsCurrentSession() {
        when(sessions.findByPrincipalName("acme:user@acme.com"))
                .thenReturn(Map.of("sess-a", sessionA, "sess-b", sessionB));

        int revoked = service.revokeOthers("acme:user@acme.com", "sess-a");

        assertEquals(1, revoked);
        verify(sessions).deleteById(eq("sess-b"));
    }

    @Test
    void revokeRejectsForeignSession() {
        when(sessions.findByPrincipalName("acme:user@acme.com")).thenReturn(Map.of("sess-a", sessionA));

        assertThrows(ResponseStatusException.class, () -> service.revoke("acme:user@acme.com", "other"));
    }
}
