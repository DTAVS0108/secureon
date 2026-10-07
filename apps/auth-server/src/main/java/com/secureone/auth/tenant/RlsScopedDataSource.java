package com.secureone.auth.tenant;

import com.secureone.auth.application.ApplicationSchemaContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Sets the row-level-security scope on every connection checkout, so queries outside a Spring
 * transaction (auto-commit JdbcTemplate calls) are scoped too. Transactions refine it with
 * transaction-local settings when they begin (ApplicationSchemaService).
 *
 * <p>Scope: an explicit {@link RlsScope#asPlatform} block, work outside any request, or a platform
 * super admin (ROLE_ADMIN) get platform scope; otherwise the request's tenant
 * ({@link TenantContext}, or the tenant of the application in {@link ApplicationSchemaContext});
 * otherwise none, which sees no tenant rows while enforcement is on.
 */
public class RlsScopedDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(RlsScopedDataSource.class);
    private static final Pattern UUID_SEGMENT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    public RlsScopedDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scoped(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scoped(super.getConnection(username, password));
    }

    private static Connection scoped(Connection connection) throws SQLException {
        try {
            boolean platform = isPlatformContext();
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null && !platform && ApplicationSchemaContext.getApplicationId() != null) {
                tenantId = tenantOfApplication(connection, ApplicationSchemaContext.getApplicationId());
            }
            if (tenantId == null && !platform) {
                reportUnscoped();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, false), set_config('app.rls_bypass', ?, false)")) {
                ps.setString(1, tenantId != null ? tenantId.toString() : "");
                ps.setString(2, platform ? "on" : "off");
                ps.execute();
            }
            return connection;
        } catch (SQLException | RuntimeException ex) {
            connection.close();
            throw ex;
        }
    }

    /** Platform scope: explicit RlsScope block, system work (no request), or a platform super admin. */
    public static boolean isPlatformContext() {
        if (RlsScope.isPlatform()) {
            return true;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static UUID tenantOfApplication(Connection connection, UUID applicationId) throws SQLException {
        try (PreparedStatement bypass = connection.prepareStatement("SELECT set_config('app.rls_bypass', 'on', false)")) {
            bypass.execute();
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT tenant_id FROM platform.application WHERE id = ?")) {
            ps.setObject(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private static void reportUnscoped() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        String where = attrs.getRequest().getMethod() + " "
                + UUID_SEGMENT.matcher(attrs.getRequest().getRequestURI()).replaceAll("{id}");
        if (REPORTED.size() < 500 && REPORTED.add(where)) {
            log.warn("RLS unscoped connection (no tenant rows visible with SECUREONE_RLS_ENFORCE=on): {}", where);
        }
    }
}
