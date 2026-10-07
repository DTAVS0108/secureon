package com.secureone.auth.scim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-tenant SCIM bearer tokens. An identity provider configured for one tenant gets a token that
 * can only provision that tenant. Only a SHA-256 hash is stored (tenant.settings.scimTokenHash).
 */
@Service
public class ScimTenantTokens {

    static final String PREFIX = "scim_";
    private static final String HASH_KEY = "scimTokenHash";

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public ScimTenantTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Issues (or replaces) the tenant's SCIM token and returns the raw value once. */
    @Transactional
    public String issue(UUID tenantId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int updated = jdbc.update(
                "UPDATE platform.tenant SET settings = jsonb_set(coalesce(settings, '{}'::jsonb), '{" + HASH_KEY + "}', to_jsonb(?::text)), updated_at = now() WHERE id = ?",
                hash(raw), tenantId);
        if (updated == 0) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }
        return raw;
    }

    @Transactional
    public void revoke(UUID tenantId) {
        jdbc.update("UPDATE platform.tenant SET settings = coalesce(settings, '{}'::jsonb) - '" + HASH_KEY + "', updated_at = now() WHERE id = ?", tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isConfigured(UUID tenantId) {
        List<Boolean> rows = jdbc.queryForList(
                // jsonb_exists, not the "?" operator: JDBC would read "?" as a bind parameter.
                "SELECT jsonb_exists(coalesce(settings, '{}'::jsonb), '" + HASH_KEY + "') FROM platform.tenant WHERE id = ?",
                Boolean.class, tenantId);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    /** The tenant a raw token belongs to, if it is a current tenant SCIM token for an active tenant. */
    @Transactional(readOnly = true)
    public Optional<UUID> resolve(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) {
            return Optional.empty();
        }
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM platform.tenant WHERE settings->>'" + HASH_KEY + "' = ? AND upper(status) = 'ACTIVE'",
                UUID.class, hash(raw));
        return ids.stream().findFirst();
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
