package com.secureone.auth.admin.tenant;

import com.secureone.auth.account.UserAccessTermination;
import com.secureone.auth.tenant.RlsScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Ends every way a tenant's users are signed in, when the tenant is suspended or deleted: their OAuth grants
 * (refresh tokens stop working), their browser sessions, and a back-channel logout to each application they used.
 * Access tokens already issued run out at their normal (short) lifetime.
 */
@Service
public class TenantAccessTermination {

    private static final Logger log = LoggerFactory.getLogger(TenantAccessTermination.class);

    private final JdbcTemplate jdbc;
    private final UserAccessTermination access;

    public TenantAccessTermination(JdbcTemplate jdbc, UserAccessTermination access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /** Returns how many users were signed out. Never throws: the suspension or deletion itself must go ahead. */
    public int endAll(UUID tenantId, String tenantSlug) {
        int count = 0;
        List<Map<String, Object>> users;
        try {
            users = RlsScope.asPlatform(() -> jdbc.queryForList(
                    "SELECT id, email FROM platform.user_account WHERE tenant_id = ?", tenantId));
        } catch (RuntimeException e) {
            log.warn("Access of tenant {} not ended: {}", tenantSlug, e.getMessage());
            return 0;
        }
        for (Map<String, Object> user : users) {
            access.endEverywhere(tenantSlug + ":" + user.get("email"), (UUID) user.get("id"));
            count++;
        }
        return count;
    }
}
