package com.secureone.auth.audit;

import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.logging.RequestContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            UUID tenantId,
            String actorEmail,
            String action,
            String targetType,
            UUID targetId,
            String targetLabel,
            boolean success) {
        record(tenantId, null, actorEmail, action, targetType, targetId, targetLabel, success);
    }

    @Transactional
    public void record(
            UUID tenantId,
            UUID applicationId,
            String actorEmail,
            String action,
            String targetType,
            UUID targetId,
            String targetLabel,
            boolean success) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setApplicationId(applicationId);
        log.setActorType("ADMIN");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(ClientIpResolver.fromMdcOrUnknown());
        String userAgent = MDC.get(RequestContext.USER_AGENT);
        if (userAgent != null && !userAgent.isBlank()) {
            log.setUserAgent(userAgent);
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("result", success ? "success" : "failure");
        meta.put("actorEmail", actorEmail != null ? actorEmail : "admin");
        meta.put("targetLabel", targetLabel);
        String principal = MDC.get(RequestContext.PRINCIPAL);
        if (principal != null && !principal.isBlank()) {
            meta.put("actorPrincipal", principal);
        }
        String requestId = MDC.get(RequestContext.REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            meta.put("requestId", requestId);
        }
        log.setMetadata(meta);
        repository.save(log);
    }
}
