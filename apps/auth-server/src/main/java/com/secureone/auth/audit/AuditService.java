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
    private final com.secureone.auth.application.ApplicationRepository applications;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<org.springframework.context.ApplicationEventPublisher> events;

    public AuditService(AuditLogRepository repository, com.secureone.auth.application.ApplicationRepository applications) {
        this.repository = repository;
        this.applications = applications;
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
        // Callers often know only the target; attribute the entry to the request's tenant and app so
        // tenant admins see it and row-level security accepts it within the tenant's scope.
        if (tenantId == null) {
            tenantId = com.secureone.auth.tenant.TenantContext.getTenantId();
        }
        if (applicationId == null) {
            UUID fromRequest = com.secureone.auth.application.ApplicationSchemaContext.getApplicationId();
            // Not when the request just deleted that application (the entry would reference a missing row).
            if (fromRequest != null && applications.existsById(fromRequest)) {
                applicationId = fromRequest;
            }
        }
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
        AuditLog saved = repository.save(log);
        // Webhooks and log streams pick it up after the change commits.
        if (events != null) events.ifAvailable(p -> p.publishEvent(new AuditRecordedEvent(saved)));
    }

    /**
     * Records in a transaction of its own, with platform scope: for end-user flows (their own two-step options,
     * sessions) whose surrounding transaction runs under the user's tenant scope or none. Never throws.
     */
    public void recordIndependently(
            UUID tenantId, UUID applicationId, String actorEmail, String action, String targetType, UUID targetId,
            String targetLabel, boolean success) {
        try {
            com.secureone.auth.tenant.RlsScope.runAsPlatform(() -> independent().executeWithoutResult(status ->
                    record(tenantId, applicationId, actorEmail, action, targetType, targetId, targetLabel, success)));
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(AuditService.class).warn("Audit {} not recorded: {}", action, e.getMessage());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager transactions;

    private org.springframework.transaction.support.TransactionTemplate independent() {
        org.springframework.transaction.support.TransactionTemplate t =
                new org.springframework.transaction.support.TransactionTemplate(transactions);
        t.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
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
