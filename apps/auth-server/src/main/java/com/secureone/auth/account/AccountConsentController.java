package com.secureone.auth.account;

import com.secureone.auth.audit.AuditService;
import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account security › Connected apps: the applications this user allowed on the consent screen, and any other
 * application that still holds a valid token for them. Removing one withdraws the consent and revokes that
 * application's tokens for this user, so it must sign them in (and ask, if it asks for consent) again.
 */
@Tag(name = "Account connected apps", description = "Third-party applications the user allowed, and withdrawing them")
@RestController
public class AccountConsentController {

    private final SessionUserResolver users;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AccountConsentController(SessionUserResolver users, JdbcTemplate jdbc, AuditService audit) {
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/v1/account/consents")
    public List<Map<String, Object>> list(Authentication authentication) {
        UserAccount user = users.requireTenantUser(authentication);
        String principal = authentication.getName();
        // Same conversion the authorization service uses when it stores expiry times.
        Timestamp now = Timestamp.from(Instant.now());
        return RlsScope.asPlatform(() -> jdbc.query("""
                WITH granted AS (
                    SELECT registered_client_id, authorities AS scopes, true AS consented
                    FROM platform.oauth2_authorization_consent
                    WHERE principal_name = ?
                    UNION ALL
                    SELECT registered_client_id, coalesce(authorized_scopes, ''), false
                    FROM platform.oauth2_authorization
                    WHERE principal_name = ? AND (refresh_token_expires_at > ? OR access_token_expires_at > ?)
                )
                SELECT g.registered_client_id, string_agg(g.scopes, ',') AS scopes, bool_or(g.consented) AS consented,
                       oc.client_id, coalesce(a.name, oc.client_name) AS name
                FROM granted g
                JOIN platform.oauth_client oc ON oc.id::text = g.registered_client_id
                LEFT JOIN platform.application a ON a.id = oc.application_id
                GROUP BY g.registered_client_id, oc.client_id, a.name, oc.client_name
                ORDER BY name
                """, (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("registered_client_id"));
                    row.put("clientId", rs.getString("client_id"));
                    row.put("name", rs.getString("name"));
                    row.put("consented", rs.getBoolean("consented"));
                    row.put("scopes", Arrays.stream(rs.getString("scopes").split(","))
                            .map(String::trim)
                            .map(s -> s.startsWith("SCOPE_") ? s.substring("SCOPE_".length()) : s)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .toList());
                    return row;
                }, principal, principal, now, now));
    }

    @DeleteMapping("/api/v1/account/consents/{registeredClientId}")
    public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable String registeredClientId) {
        UserAccount user = users.requireTenantUser(authentication);
        String principal = authentication.getName();
        int removed = RlsScope.asPlatform(() -> {
            int consent = jdbc.update(
                    "DELETE FROM platform.oauth2_authorization_consent WHERE registered_client_id = ? AND principal_name = ?",
                    registeredClientId, principal);
            // Its access and refresh tokens stop working too (also for apps that never asked for consent).
            int tokens = jdbc.update(
                    "DELETE FROM platform.oauth2_authorization WHERE registered_client_id = ? AND principal_name = ?",
                    registeredClientId, principal);
            return consent + tokens;
        });
        if (removed == 0) {
            return ResponseEntity.notFound().build();
        }
        audit.record(user.getTenantId(), null, user.getEmail(), "user.consent_revoked", "oauth_client", null,
                registeredClientId, true);
        return ResponseEntity.noContent().build();
    }
}
