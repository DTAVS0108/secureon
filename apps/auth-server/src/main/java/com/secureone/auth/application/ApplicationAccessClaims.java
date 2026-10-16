package com.secureone.auth.application;

import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * A user's effective roles and permissions in one application, for tokens and /userinfo: roles
 * assigned directly, roles granted through groups, and the children of composite roles (recursively),
 * plus every permission those roles carry. Queries name the application's schema explicitly, so the
 * result does not depend on the request's search_path.
 */
@Service
public class ApplicationAccessClaims {

    public record Access(List<String> roles, List<String> permissions) {
        static final Access NONE = new Access(List.of(), List.of());
    }

    private final JdbcTemplate jdbc;
    private final ApplicationOAuthClientResolver clients;
    private final TenantRepository tenants;
    private final UserAccountRepository users;

    public ApplicationAccessClaims(
            JdbcTemplate jdbc,
            ApplicationOAuthClientResolver clients,
            TenantRepository tenants,
            UserAccountRepository users) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.tenants = tenants;
        this.users = users;
    }

    /** For a principal named {@code tenantSlug:email} signing in through an OAuth client. */
    public Access forClient(String oauthClientId, String principalName) {
        return RlsScope.asPlatform(() -> {
            Optional<Application> app = clients.findActiveByOAuthClientId(oauthClientId);
            Optional<UUID> userId = resolveUser(principalName);
            if (app.isEmpty() || userId.isEmpty()) {
                return Access.NONE;
            }
            return forUser(app.get(), userId.get());
        });
    }

    private Access forUser(Application app, UUID userId) {
        String s = schema(app);
        String effectiveRoles = """
                WITH RECURSIVE direct AS (
                    SELECT ur.role_id FROM %1$s.user_role ur
                    JOIN %1$s.role r ON r.id = ur.role_id
                    WHERE ur.user_id = ? AND r.application_id = ?
                    UNION
                    SELECT gr.role_id FROM %1$s.rbac_group_member gm
                    JOIN %1$s.rbac_group g ON g.id = gm.group_id
                    JOIN %1$s.rbac_group_role gr ON gr.group_id = g.id
                    WHERE gm.user_id = ? AND g.application_id = ?
                ), expanded(role_id) AS (
                    SELECT role_id FROM direct
                    UNION
                    SELECT rc.child_role_id FROM %1$s.role_composite rc JOIN expanded e ON rc.parent_role_id = e.role_id
                )
                """.formatted(s);
        Object[] args = {userId, app.getId(), userId, app.getId()};
        List<String> roles = jdbc.queryForList(
                effectiveRoles + "SELECT r.name FROM " + s + ".role r WHERE r.id IN (SELECT role_id FROM expanded) ORDER BY r.name",
                String.class,
                args);
        List<String> permissions = jdbc.queryForList(
                effectiveRoles + "SELECT DISTINCT p.key FROM " + s + ".role_permission rp JOIN " + s
                        + ".permission p ON p.id = rp.permission_id WHERE rp.role_id IN (SELECT role_id FROM expanded) ORDER BY p.key",
                String.class,
                args);
        return new Access(roles, permissions);
    }

    private Optional<UUID> resolveUser(String principalName) {
        if (principalName == null) {
            return Optional.empty();
        }
        int sep = principalName.indexOf(':');
        if (sep <= 0) {
            return Optional.empty();
        }
        String slug = principalName.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = principalName.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return tenants.findBySlug(slug)
                .flatMap(t -> users.findByTenantIdAndEmail(t.getId(), email))
                .map(u -> u.getId());
    }

    private static String schema(Application app) {
        String name = app.getSchemaName() == null || app.getSchemaName().isBlank()
                ? ApplicationSchemaNames.PLATFORM_SCHEMA
                : app.getSchemaName();
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
