package com.secureone.auth.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.logging.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capturesClientIpAndActorFromMdc() {
        MDC.put(RequestContext.CLIENT_IP, "203.0.113.55");
        MDC.put(RequestContext.PRINCIPAL, "acme:admin@acme.com");
        MDC.put(RequestContext.REQUEST_ID, "req-123");
        MDC.put(RequestContext.USER_AGENT, "SecureOne-Test/1.0");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AuditService service = new AuditService(repository, org.mockito.Mockito.mock(com.secureone.auth.application.ApplicationRepository.class));

        service.record(null, null, "ops@example.com", "user.update", "user", null, "Jane", true);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("203.0.113.55", saved.getIp());
        assertEquals("SecureOne-Test/1.0", saved.getUserAgent());
        assertEquals("req-123", saved.getMetadata().get("requestId"));
        assertEquals("acme:admin@acme.com", saved.getMetadata().get("actorPrincipal"));
    }
}
