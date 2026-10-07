package com.secureone.auth.audit;

import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.logging.RequestContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        String principal = currentPrincipal();
        meta.put("actorEmail", resolveActor(actorEmail, principal));
        meta.put("targetLabel", targetLabel);
        if (principal != null) {
            meta.put("actorPrincipal", principal);
        }
        String requestId = MDC.get(RequestContext.REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            meta.put("requestId", requestId);
        }
        log.setMetadata(meta);
        repository.save(log);
    }

    /** Callers pass "admin" as a placeholder; record the operator who is actually signed in. */
    static String resolveActor(String given, String principal) {
        boolean placeholder = given == null || given.isBlank() || "admin".equalsIgnoreCase(given);
        if (placeholder && principal != null) {
            return principal;
        }
        return given != null && !given.isBlank() ? given : "system";
    }

    private static String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        String fromMdc = MDC.get(RequestContext.PRINCIPAL);
        return fromMdc != null && !fromMdc.isBlank() ? fromMdc : null;
    }
}
