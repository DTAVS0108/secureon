package com.secureone.auth.application;

import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ApplicationSchemaService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSchemaService.class);
    private static final Pattern UUID_SEGMENT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final int MAX_REPORTED_UNSCOPED = 500;

    private final ApplicationRepository applications;
    private final JdbcTemplate jdbc;
    private final boolean rlsEnforced;
    private final Set<String> reportedUnscoped = ConcurrentHashMap.newKeySet();

    public ApplicationSchemaService(
            ApplicationRepository applications,
            JdbcTemplate jdbc,
            @Value("${secureone.rls.enforce:off}") String rlsEnforce) {
        this.applications = applications;
        this.jdbc = jdbc;
        this.rlsEnforced = "on".equalsIgnoreCase(rlsEnforce) || "true".equalsIgnoreCase(rlsEnforce);
    }

    /** Called from {@link ApplicationSchemaJpaTransactionManager#doBegin} on every DB transaction. */
    public void applySearchPathForContext() {
        // The lookups below read RLS-protected tables before the transaction's scope is known.
        jdbc.queryForObject("SELECT set_config('app.rls_bypass', 'on', true)", String.class);
        UUID applicationId = ApplicationSchemaContext.getApplicationId();
        if (applicationId == null) {
            applyPlatformSearchPath();
        } else {
            Application app = applications.findById(applicationId).orElse(null);
            applySearchPathForApplication(app);
        }
        applyTenantRlsForContext();
    }

    public void applySearchPathForApplicationId(UUID applicationId) {
        if (applicationId == null) {
            applyPlatformSearchPath();
            return;
        }
        Application app = applications.findById(applicationId).orElse(null);
        applySearchPathForApplication(app);
    }

    public void applySearchPathForApplication(Application app) {
        if (app == null || app.getSchemaName() == null || app.getSchemaName().isBlank()) {
            applyPlatformSearchPath();
            return;
        }
        jdbc.execute(
                "SET LOCAL search_path TO " + quoteIdent(app.getSchemaName()) + ", "
                        + quoteIdent(ApplicationSchemaNames.PLATFORM_SCHEMA));
    }

    public void applyPlatformSearchPath() {
        jdbc.execute("SET LOCAL search_path TO " + quoteIdent(ApplicationSchemaNames.PLATFORM_SCHEMA));
    }

    /**
     * Sets the transaction's RLS scope (see V61): a tenant, explicit platform scope, or unscoped.
     * Unscoped sees every row while {@code secureone.rls.enforce} is off and none once it is on.
     */
    public void applyTenantRlsForContext() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            UUID applicationId = ApplicationSchemaContext.getApplicationId();
            if (applicationId != null) {
                tenantId = applications.findById(applicationId).map(Application::getTenantId).orElse(null);
            }
        }
        // An explicit RlsScope.asPlatform block wins over the request's tenant: it marks a deliberate
        // cross-tenant lookup (sign-in, session restore). Same rules as RlsScopedDataSource.
        boolean platform = RlsScope.isPlatform()
                || (tenantId == null && com.secureone.auth.tenant.RlsScopedDataSource.isPlatformContext());
        if (tenantId == null && !platform) {
            reportUnscoped();
        }
        jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true) || set_config('app.rls_bypass', ?, true)",
                String.class,
                tenantId != null ? tenantId.toString() : "",
                platform ? "on" : "off");
    }

    /** Logs each distinct request path that runs without a tenant or platform scope, once. */
    private void reportUnscoped() {
        String where = currentRequestKey();
        if (reportedUnscoped.size() >= MAX_REPORTED_UNSCOPED || !reportedUnscoped.add(where)) {
            return;
        }
        if (rlsEnforced) {
            log.warn("RLS unscoped transaction (tenant rows hidden): {}", where);
        } else {
            log.warn("RLS unscoped transaction (would be denied with SECUREONE_RLS_ENFORCE=on): {}", where);
        }
    }

    private static String currentRequestKey() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getMethod() + " " + UUID_SEGMENT.matcher(request.getRequestURI()).replaceAll("{id}");
        }
        StackTraceElement caller = StackWalker.getInstance()
                .walk(frames -> frames
                        .filter(f -> f.getClassName().startsWith("com.secureone.")
                                && !f.getClassName().startsWith("com.secureone.auth.application.ApplicationSchema"))
                        .findFirst()
                        .map(StackWalker.StackFrame::toStackTraceElement)
                        .orElse(null));
        return "non-request " + (caller != null ? caller.getClassName() + "." + caller.getMethodName() : Thread.currentThread().getName());
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
