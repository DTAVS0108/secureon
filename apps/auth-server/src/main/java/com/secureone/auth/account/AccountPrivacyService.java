package com.secureone.auth.account;

import com.secureone.auth.audit.AuditLog;
import com.secureone.auth.audit.AuditLogRepository;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.federation.UserIdentityRepository;
import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.mfa.MfaFactor;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountPrivacyService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final TenantUserDetailsService userDetails;
    private final MfaFactorRepository mfaFactors;
    private final UserIdentityRepository identities;
    private final AuditLogRepository auditLogs;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<UserAccessTermination> accessTermination;

    public AccountPrivacyService(
            UserAccountRepository users,
            TenantRepository tenants,
            TenantUserDetailsService userDetails,
            MfaFactorRepository mfaFactors,
            UserIdentityRepository identities,
            AuditLogRepository auditLogs) {
        this.users = users;
        this.tenants = tenants;
        this.userDetails = userDetails;
        this.mfaFactors = mfaFactors;
        this.identities = identities;
        this.auditLogs = auditLogs;
    }

    public Map<String, Object> export(Jwt jwt) {
        return exportForUser(resolveUser(jwt));
    }

    public Map<String, String> requestDeletion(Jwt jwt) {
        return requestDeletionForUser(resolveUser(jwt));
    }

    public Map<String, Object> exportForUser(UserAccount user) {
        Tenant tenant = tenants.findById(user.getTenantId()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("userId", user.getId());
        out.put("email", user.getEmail());
        out.put("emailVerified", user.isEmailVerified());
        out.put("displayName", user.getDisplayName());
        out.put("status", user.getStatus());
        out.put("createdAt", user.getCreatedAt());
        out.put("updatedAt", user.getUpdatedAt());
        out.put("lastLoginAt", user.getLastLoginAt());
        if (tenant != null) {
            out.put("tenantId", tenant.getId());
            out.put("tenantSlug", tenant.getSlug());
            out.put("tenantName", tenant.getName());
        }
        List<Map<String, Object>> factors = new ArrayList<>();
        for (MfaFactor factor : mfaFactors.findByUserIdOrderByCreatedAtAsc(user.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", factor.getId());
            row.put("type", factor.getType());
            row.put("verified", factor.isVerified());
            row.put("createdAt", factor.getCreatedAt());
            factors.add(row);
        }
        out.put("mfaFactors", factors);
        out.put("linkedIdentityCount", identities.findByUserId(user.getId()).size());
        out.put("username", user.getUsername());
        out.put("phone", user.getPhone());
        out.put("phoneVerified", user.isPhoneVerified());
        Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes() != null ? user.getAttributes() : Map.of());
        attributes.keySet().removeAll(INTERNAL_ATTRIBUTES);
        out.put("attributes", attributes);
        if (jdbc != null && tenant != null) {
            addActivity(out, user, tenant.getSlug() + ":" + user.getEmail());
        }
        return out;
    }

    /** Kept by SecureOne for its own use (pending changes, method choices): not the user's profile data. */
    private static final java.util.Set<String> INTERNAL_ATTRIBUTES = java.util.Set.of(
            "pendingEmail", "pendingEmailApplicationId", "pendingInviteApplicationId", "authMethods");

    /** Sign-in history, the apps that can use the account, application memberships and roles, trusted devices. */
    private void addActivity(Map<String, Object> out, UserAccount user, String principal) {
        out.put("signInHistory", jdbc.queryForList("""
                SELECT created_at AS "at", result, ip, device, application_id AS "applicationId"
                FROM platform.login_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 500
                """, user.getId()));
        out.put("connectedApps", jdbc.queryForList("""
                SELECT DISTINCT oc.client_id AS "clientId", coalesce(a.name, oc.client_name) AS "name"
                FROM (SELECT registered_client_id FROM platform.oauth2_authorization WHERE principal_name = ?
                      UNION SELECT registered_client_id FROM platform.oauth2_authorization_consent WHERE principal_name = ?) g
                JOIN platform.oauth_client oc ON oc.id::text = g.registered_client_id
                LEFT JOIN platform.application a ON a.id = oc.application_id
                """, principal, principal));
        List<Map<String, Object>> applications = new ArrayList<>();
        for (Map<String, Object> app : jdbc.queryForList(
                "SELECT id, name, schema_name FROM platform.application WHERE tenant_id = ? AND schema_name IS NOT NULL",
                user.getTenantId())) {
            String schema = String.valueOf(app.get("schema_name"));
            if (!schema.matches("[a-z][a-z0-9_]{0,62}")) {
                continue;
            }
            try {
                Integer member = jdbc.queryForObject("SELECT count(*) FROM \"" + schema + "\".user_application WHERE user_id = ?",
                        Integer.class, user.getId());
                if (member == null || member == 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("application", app.get("name"));
                row.put("roles", jdbc.queryForList("SELECT r.name FROM \"" + schema + "\".user_role ur JOIN \"" + schema
                        + "\".role r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class, user.getId()));
                applications.add(row);
            } catch (RuntimeException e) {
                // A schema from an older layout: left out rather than failing the whole export.
            }
        }
        out.put("applications", applications);
        out.put("trustedDevices", jdbc.queryForList("""
                SELECT label, created_at AS "createdAt", last_used_at AS "lastUsedAt", expires_at AS "expiresAt"
                FROM platform.trusted_device WHERE user_id = ? ORDER BY created_at
                """, user.getId()));
    }

    public Map<String, String> requestDeletionForUser(UserAccount user) {
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            return Map.of(
                    "message",
                    "Your account is already disabled. Contact your administrator to complete removal.");
        }
        user.setStatus("DISABLED");
        users.save(user);
        // Listed for the tenant's administrators; removed for good after 30 days if nobody does it sooner.
        if (jdbc != null) {
            jdbc.update("INSERT INTO platform.account_deletion_request (user_id, tenant_id) VALUES (?, ?)"
                    + " ON CONFLICT (user_id) DO NOTHING", user.getId(), user.getTenantId());
        }
        recordUserAudit(user, "account.deletion_requested", true);
        // Disabled from now: the apps' refresh tokens and every browser session end with it.
        if (accessTermination != null) {
            accessTermination.ifAvailable(t -> t.endEverywhere(user));
        }
        return Map.of(
                "message",
                "Account deletion requested. Your account has been disabled and will be removed within 30 days.");
    }

    private void recordUserAudit(UserAccount user, String action, boolean success) {
        AuditLog log = new AuditLog();
        log.setTenantId(user.getTenantId());
        log.setActorType("USER");
        log.setActorId(user.getId());
        log.setAction(action);
        log.setTargetType("USER");
        log.setTargetId(user.getId());
        log.setIp(ClientIpResolver.fromMdcOrUnknown());
        Map<String, Object> meta = new HashMap<>();
        meta.put("result", success ? "success" : "failure");
        meta.put("actorEmail", user.getEmail());
        log.setMetadata(meta);
        auditLogs.save(log);
    }

    private UserAccount resolveUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("Token subject is missing.");
        }
        try {
            UUID id = UUID.fromString(subject);
            return users.findById(id).orElseThrow(() -> new BadCredentialsException("User not found."));
        } catch (IllegalArgumentException ignored) {
            // Fall through — subject is tenantSlug:email
        }
        int sep = subject.indexOf(':');
        if (sep <= 0) {
            throw new BadCredentialsException("Unrecognized token subject.");
        }
        String tenantSlug = subject.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = subject.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return userDetails.resolveAccount(tenantSlug, email);
    }
}
