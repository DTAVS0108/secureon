package com.secureone.auth.oauth.registration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientUpdateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminService;
import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.tenant.RlsScope;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Dynamic client registration (RFC 7591, OIDC Dynamic Client Registration 1.0) and client self-management
 * (RFC 7592), per application, as Keycloak does it per realm: off (the default), with an initial access token an
 * administrator hands out (a number of registrations until it expires), or open (no token; on the tenant's own
 * issuer, for one application of the tenant, rate limited).
 *
 * <p>Policies limit what a registered client may ask for: the application's allowed scopes and grant types, redirect
 * URIs on trusted hosts only (when listed), and a maximum number of registered clients. Registered clients always
 * show the consent screen. Clients never used are removed after the application's "unused days".
 */
@Service
public class ClientRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ClientRegistrationService.class);

    public static final String MODE_OFF = "off";
    public static final String MODE_TOKEN = "token";
    public static final String MODE_OPEN = "open";
    static final Set<String> GRANT_TYPES = Set.of("authorization_code", "refresh_token", "client_credentials");
    static final Set<String> AUTH_METHODS = Set.of("none", "client_secret_basic", "client_secret_post", "private_key_jwt");
    static final int OPEN_PER_MINUTE = 10;
    private static final String TOKEN_PREFIX = "s1rt_";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** An application's registration settings. */
    public record Policy(
            UUID applicationId, UUID tenantId, String mode, List<String> allowedScopes, List<String> allowedGrantTypes,
            List<String> trustedHosts, int maxClients, int unusedDays) {

        public boolean enabled() {
            return !MODE_OFF.equals(mode);
        }
    }

    public record InitialToken(UUID id, String label, int maxUses, int uses, Instant expiresAt, Instant revokedAt, Instant createdAt) {

        public boolean usable() {
            return revokedAt == null && uses < maxUses && expiresAt.isAfter(Instant.now());
        }
    }

    /** A refused registration: the RFC 7591 error code, its description, and the HTTP status. */
    public static final class RegistrationException extends RuntimeException {
        private final String error;
        private final int status;

        public RegistrationException(int status, String error, String description) {
            super(description);
            this.status = status;
            this.error = error;
        }

        public String error() {
            return error;
        }

        public int status() {
            return status;
        }
    }

    private final JdbcTemplate jdbc;
    private final OAuthClientRepository clients;
    private final OAuthClientAdminService clientAdmin;
    private final ObjectProvider<com.secureone.auth.audit.AuditService> audit;
    private final ObjectProvider<com.secureone.auth.ratelimit.RedisRateLimiter> rateLimiter;

    public ClientRegistrationService(
            JdbcTemplate jdbc,
            OAuthClientRepository clients,
            OAuthClientAdminService clientAdmin,
            ObjectProvider<com.secureone.auth.audit.AuditService> audit,
            ObjectProvider<com.secureone.auth.ratelimit.RedisRateLimiter> rateLimiter) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.clientAdmin = clientAdmin;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    // ---- settings ----

    public Policy policy(UUID applicationId, UUID tenantId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.application_client_registration WHERE application_id = ?", this::policyRow, applicationId)
                .stream().findFirst()
                .orElse(new Policy(applicationId, tenantId, MODE_OFF, List.of("openid", "profile", "email"),
                        List.of("authorization_code", "refresh_token"), List.of(), 100, 30)));
    }

    /** Saves the settings; a field left out keeps its value. */
    public Policy savePolicy(UUID applicationId, UUID tenantId, Map<String, Object> body) {
        Policy current = policy(applicationId, tenantId);
        String mode = body.containsKey("mode") ? String.valueOf(body.get("mode")).trim().toLowerCase(Locale.ROOT) : current.mode();
        if (!Set.of(MODE_OFF, MODE_TOKEN, MODE_OPEN).contains(mode)) {
            throw new IllegalArgumentException("mode must be off, token or open.");
        }
        List<String> scopes = body.containsKey("allowedScopes") ? strings(body.get("allowedScopes"), "allowedScopes") : current.allowedScopes();
        for (String scope : scopes) {
            if (!scope.matches("[A-Za-z0-9][A-Za-z0-9:._/\\-]{0,99}")) {
                throw new IllegalArgumentException("Scope \"" + scope + "\" is not a valid scope name.");
            }
        }
        List<String> grants = body.containsKey("allowedGrantTypes") ? strings(body.get("allowedGrantTypes"), "allowedGrantTypes") : current.allowedGrantTypes();
        for (String grant : grants) {
            if (!GRANT_TYPES.contains(grant)) {
                throw new IllegalArgumentException("allowedGrantTypes: " + grant + " is not one of " + new java.util.TreeSet<>(GRANT_TYPES) + ".");
            }
        }
        if (grants.isEmpty()) {
            throw new IllegalArgumentException("Allow at least one grant type.");
        }
        List<String> hosts = body.containsKey("trustedHosts") ? strings(body.get("trustedHosts"), "trustedHosts") : current.trustedHosts();
        List<String> normalizedHosts = new ArrayList<>();
        for (String host : hosts) {
            String h = host.trim().toLowerCase(Locale.ROOT);
            if (!h.matches("(\\*\\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*")) {
                throw new IllegalArgumentException("Trusted host \"" + host + "\" must be a host name such as app.example.com or *.example.com.");
            }
            normalizedHosts.add(h);
        }
        int max = body.get("maxClients") instanceof Number n ? n.intValue() : current.maxClients();
        int unused = body.get("unusedDays") instanceof Number n ? n.intValue() : current.unusedDays();
        if (max < 1 || max > 10000) {
            throw new IllegalArgumentException("maxClients must be 1 to 10000.");
        }
        if (unused < 0 || unused > 3650) {
            throw new IllegalArgumentException("unusedDays must be 0 (never) to 3650.");
        }
        if (MODE_OPEN.equals(mode)) {
            Integer others = RlsScope.asPlatform(() -> jdbc.queryForObject(
                    "SELECT count(*) FROM platform.application_client_registration WHERE tenant_id = ? AND mode = 'open' AND application_id <> ?",
                    Integer.class, tenantId, applicationId));
            if (others != null && others > 0) {
                throw new com.secureone.auth.admin.ConflictException(
                        "Another application of this organization already takes open registrations; only one can.");
            }
            if (grants.contains("client_credentials")) {
                throw new IllegalArgumentException("Open registration cannot allow client_credentials: anyone could get machine tokens.");
            }
        }
        String finalMode = mode;
        RlsScope.runAsPlatform(() -> jdbc.update(
                "INSERT INTO platform.application_client_registration (application_id, tenant_id, mode, allowed_scopes, allowed_grant_types, "
                        + "trusted_hosts, max_clients, unused_days, updated_at) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, now()) "
                        + "ON CONFLICT (application_id) DO UPDATE SET mode = EXCLUDED.mode, allowed_scopes = EXCLUDED.allowed_scopes, "
                        + "allowed_grant_types = EXCLUDED.allowed_grant_types, trusted_hosts = EXCLUDED.trusted_hosts, "
                        + "max_clients = EXCLUDED.max_clients, unused_days = EXCLUDED.unused_days, updated_at = now()",
                applicationId, tenantId, finalMode, json(scopes), json(grants), json(normalizedHosts), max, unused));
        audit.ifAvailable(a -> a.record(tenantId, applicationId, "admin", "client_registration.settings_changed",
                "application", applicationId, "mode " + finalMode, true));
        return policy(applicationId, tenantId);
    }

    /** Whether any application (of the tenant, or anywhere when null) takes registrations: discovery then names the endpoint. */
    public boolean anyEnabled(UUID tenantId) {
        Integer count = RlsScope.asPlatform(() -> tenantId == null
                ? jdbc.queryForObject("SELECT count(*) FROM platform.application_client_registration WHERE mode <> 'off'", Integer.class)
                : jdbc.queryForObject("SELECT count(*) FROM platform.application_client_registration WHERE mode <> 'off' AND tenant_id = ?",
                        Integer.class, tenantId));
        return count != null && count > 0;
    }

    // ---- initial access tokens ----

    public record CreatedToken(InitialToken token, String value) {}

    public CreatedToken createToken(UUID applicationId, UUID tenantId, String label, int maxUses, int expiresInHours) {
        if (maxUses < 1 || maxUses > 1000) {
            throw new IllegalArgumentException("maxUses must be 1 to 1000.");
        }
        if (expiresInHours < 1 || expiresInHours > 8760) {
            throw new IllegalArgumentException("expiresInHours must be 1 to 8760 (a year).");
        }
        String value = TOKEN_PREFIX + randomToken();
        UUID id = UUID.randomUUID();
        String cleanLabel = label == null || label.isBlank() ? null : label.trim().substring(0, Math.min(200, label.trim().length()));
        RlsScope.runAsPlatform(() -> jdbc.update(
                "INSERT INTO platform.client_registration_token (id, application_id, token_hash, label, max_uses, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, applicationId, sha256(value), cleanLabel, maxUses,
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofHours(expiresInHours)))));
        audit.ifAvailable(a -> a.record(tenantId, applicationId, "admin", "client_registration.token_created",
                "client_registration_token", id, cleanLabel != null ? cleanLabel : "initial access token", true));
        return new CreatedToken(tokens(applicationId).stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow(), value);
    }

    public List<InitialToken> tokens(UUID applicationId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.client_registration_token WHERE application_id = ? ORDER BY created_at DESC",
                this::tokenRow, applicationId));
    }

    public void revokeToken(UUID applicationId, UUID tenantId, UUID tokenId) {
        int updated = RlsScope.asPlatform(() -> jdbc.update(
                "UPDATE platform.client_registration_token SET revoked_at = now() WHERE id = ? AND application_id = ? AND revoked_at IS NULL",
                tokenId, applicationId));
        if (updated == 0) {
            throw new com.secureone.auth.admin.ResourceNotFoundException("Token not found or already revoked: " + tokenId);
        }
        audit.ifAvailable(a -> a.record(tenantId, applicationId, "admin", "client_registration.token_revoked",
                "client_registration_token", tokenId, "initial access token", true));
    }

    // ---- registration (RFC 7591) ----

    /**
     * Registers a client. {@code bearer}: the initial access token, if sent. {@code targetTenant}: the tenant whose
     * issuer the request addressed (null: the shared issuer, where a token is needed). Returns the RFC 7591 response.
     */
    public Map<String, Object> register(String bearer, UUID targetTenant, String clientIp, Map<String, Object> metadata,
            String registrationBase) {
        Policy policy;
        UUID tokenId = null;
        if (bearer != null && !bearer.isBlank()) {
            Optional<Map.Entry<UUID, UUID>> token = usableToken(bearer);
            if (token.isEmpty()) {
                throw new RegistrationException(401, "invalid_token", "The initial access token is not valid (unknown, used up, expired or revoked).");
            }
            tokenId = token.get().getKey();
            UUID applicationId = token.get().getValue();
            UUID tenantId = tenantOf(applicationId);
            policy = policy(applicationId, tenantId);
            if (!policy.enabled()) {
                throw new RegistrationException(403, "access_denied", "Client registration is turned off for this application.");
            }
            if (targetTenant != null && !targetTenant.equals(tenantId)) {
                throw new RegistrationException(401, "invalid_token", "This token belongs to another organization's issuer.");
            }
        } else {
            if (targetTenant == null) {
                throw new RegistrationException(401, "invalid_token",
                        "An initial access token is required (open registration is only on an organization's own issuer).");
            }
            policy = openPolicy(targetTenant).orElseThrow(() -> new RegistrationException(401, "invalid_token",
                    "An initial access token is required: this organization does not take open registrations."));
            com.secureone.auth.ratelimit.RedisRateLimiter limiter = rateLimiter.getIfAvailable();
            if (limiter != null && !limiter.tryConsume("client-registration:" + clientIp, OPEN_PER_MINUTE)) {
                throw new RegistrationException(429, "access_denied", "Too many registrations from this address; try again in a minute.");
            }
        }
        Policy p = policy;
        long registered = RlsScope.asPlatform(() -> clients.findByApplicationIdOrderByCreatedAtDesc(p.applicationId()).stream()
                .filter(OAuthClient::isDynamicallyRegistered).count());
        if (registered >= policy.maxClients()) {
            throw new RegistrationException(400, "invalid_client_metadata", "This application has reached its limit of registered clients.");
        }
        Requested requested = validate(metadata, policy);
        if (tokenId != null && !consume(tokenId)) {
            throw new RegistrationException(401, "invalid_token", "The initial access token is used up.");
        }
        String clientId = "dcr-" + HexFormat.of().formatHex(randomBytes(12));
        var created = RlsScope.asPlatform(() -> clientAdmin.create(new OAuthClientCreateRequest(
                p.applicationId(), null, null, clientId, requested.type(), "active", requested.grantTypes(), requested.scopes(),
                requested.redirectUris(), requested.postLogoutRedirectUris(), "none".equals(requested.authMethod()) || requested.redirectUris().size() > 0,
                requested.authMethod(), "user_id", null, null, true, requested.jwksUri(), requested.jwks())));
        String registrationToken = TOKEN_PREFIX + randomToken();
        String echo = json(requested.echo());
        RlsScope.runAsPlatform(() -> clients.findByClientId(clientId).ifPresent(c -> {
            c.setDynamicallyRegistered(true);
            c.setRegistrationTokenHash(sha256(registrationToken));
            c.setRegistrationMetadata(echo);
            clients.save(c);
        }));
        audit.ifAvailable(a -> a.record(p.tenantId(), p.applicationId(), "client registration", "oauth_client.registered",
                "oauth_client", created.client().id(), clientId + " (" + requested.echo().getOrDefault("client_name", "unnamed") + ")", true));
        Map<String, Object> response = response(clientId, created.clientSecret(), requested.echo(), registrationBase);
        response.put("registration_access_token", registrationToken);
        return response;
    }

    // ---- management (RFC 7592) ----

    public Map<String, Object> read(String clientId, String bearer, String registrationBase) {
        OAuthClient client = authorized(clientId, bearer);
        String rotated = rotate(client);
        Map<String, Object> out = response(clientId, null, metadataOf(client), registrationBase);
        out.put("registration_access_token", rotated);
        return out;
    }

    public Map<String, Object> update(String clientId, String bearer, Map<String, Object> metadata, String registrationBase) {
        OAuthClient client = authorized(clientId, bearer);
        Object sent = metadata.get("client_id");
        if (sent != null && !clientId.equals(String.valueOf(sent))) {
            throw new RegistrationException(400, "invalid_client_metadata", "client_id in the body must be the client's own.");
        }
        UUID tenantId = tenantOf(client.getApplicationId());
        Requested requested = validate(metadata, policy(client.getApplicationId(), tenantId));
        if (!requested.type().equals(client.getType())) {
            throw new RegistrationException(400, "invalid_client_metadata",
                    "A registered client keeps its kind (" + client.getType() + "); register a new client instead.");
        }
        RlsScope.runAsPlatform(() -> clientAdmin.update(client.getId(), new OAuthClientUpdateRequest(
                null, null, requested.grantTypes(), requested.scopes(), requested.redirectUris(), requested.postLogoutRedirectUris(),
                null, requested.authMethod(), null, null, null, true,
                requested.jwksUri() == null ? "" : requested.jwksUri(), requested.jwks() == null ? Map.of() : requested.jwks())));
        String echo = json(requested.echo());
        String rotated = TOKEN_PREFIX + randomToken();
        RlsScope.runAsPlatform(() -> clients.findById(client.getId()).ifPresent(c -> {
            c.setRegistrationMetadata(echo);
            c.setRegistrationTokenHash(sha256(rotated));
            clients.save(c);
        }));
        audit.ifAvailable(a -> a.record(tenantId, client.getApplicationId(), "client registration", "oauth_client.registration_updated",
                "oauth_client", client.getId(), clientId, true));
        Map<String, Object> out = response(clientId, null, requested.echo(), registrationBase);
        out.put("registration_access_token", rotated);
        return out;
    }

    public void delete(String clientId, String bearer) {
        OAuthClient client = authorized(clientId, bearer);
        UUID tenantId = tenantOf(client.getApplicationId());
        RlsScope.runAsPlatform(() -> clientAdmin.delete(client.getId()));
        audit.ifAvailable(a -> a.record(tenantId, client.getApplicationId(), "client registration", "oauth_client.registration_deleted",
                "oauth_client", client.getId(), clientId, true));
    }

    /** Daily: registered clients never used (no grant or token ever) for the application's "unused days" are removed. */
    @Scheduled(cron = "${secureone.client-registration.cleanup-cron:0 50 3 * * *}")
    public void removeUnused() {
        List<Map<String, Object>> stale = RlsScope.asPlatform(() -> jdbc.queryForList(
                "SELECT c.id, c.client_id, c.application_id, r.tenant_id FROM platform.oauth_client c "
                        + "JOIN platform.application_client_registration r ON r.application_id = c.application_id "
                        + "WHERE c.dynamically_registered AND r.unused_days > 0 "
                        + "AND c.created_at < now() - make_interval(days => r.unused_days) "
                        + "AND NOT EXISTS (SELECT 1 FROM platform.oauth2_authorization a WHERE a.registered_client_id = c.id::text)"));
        for (Map<String, Object> row : stale) {
            UUID id = (UUID) row.get("id");
            try {
                RlsScope.runAsPlatform(() -> clientAdmin.delete(id));
                audit.ifAvailable(a -> a.record((UUID) row.get("tenant_id"), (UUID) row.get("application_id"), "client registration",
                        "oauth_client.registration_deleted", "oauth_client", id, row.get("client_id") + " (never used)", true));
            } catch (RuntimeException e) {
                log.warn("Could not remove unused registered client {}: {}", row.get("client_id"), e.getMessage());
            }
        }
    }

    // ---- helpers ----

    /** What a validated registration asks for, in SecureOne's terms, and the metadata echoed back. */
    record Requested(String type, String authMethod, List<String> grantTypes, List<String> scopes, List<String> redirectUris,
            List<String> postLogoutRedirectUris, String jwksUri, Map<String, Object> jwks, Map<String, Object> echo) {}

    @SuppressWarnings("unchecked")
    Requested validate(Map<String, Object> m, Policy policy) {
        List<String> grants = m.containsKey("grant_types") ? metaStrings(m.get("grant_types"), "grant_types") : List.of("authorization_code");
        for (String grant : grants) {
            if (!policy.allowedGrantTypes().contains(grant)) {
                throw new RegistrationException(400, "invalid_client_metadata",
                        "grant_types: " + grant + " is not allowed here (allowed: " + String.join(", ", policy.allowedGrantTypes()) + ").");
            }
        }
        boolean code = grants.contains("authorization_code");
        if (grants.contains("refresh_token") && !code) {
            throw new RegistrationException(400, "invalid_client_metadata", "refresh_token needs authorization_code.");
        }
        List<String> responseTypes = m.containsKey("response_types") ? metaStrings(m.get("response_types"), "response_types")
                : code ? List.of("code") : List.of();
        if (code ? !responseTypes.equals(List.of("code")) : !responseTypes.isEmpty()) {
            throw new RegistrationException(400, "invalid_client_metadata",
                    "response_types must be [\"code\"] with authorization_code (and empty without it).");
        }
        List<String> redirects = m.containsKey("redirect_uris") ? metaStrings(m.get("redirect_uris"), "redirect_uris") : List.of();
        if (code && redirects.isEmpty()) {
            throw new RegistrationException(400, "invalid_redirect_uri", "redirect_uris is required for authorization_code.");
        }
        if (!code && !redirects.isEmpty()) {
            throw new RegistrationException(400, "invalid_redirect_uri", "redirect_uris are only for authorization_code.");
        }
        List<String> postLogout = m.containsKey("post_logout_redirect_uris")
                ? metaStrings(m.get("post_logout_redirect_uris"), "post_logout_redirect_uris") : List.of();
        for (String uri : concat(redirects, postLogout)) {
            String problem = com.secureone.auth.admin.oauth.RedirectUriValidator.problem(uri);
            if (problem != null) {
                throw new RegistrationException(400, "invalid_redirect_uri", uri + ": " + problem);
            }
            if (!policy.trustedHosts().isEmpty() && !trusted(uri, policy.trustedHosts())) {
                throw new RegistrationException(400, "invalid_redirect_uri", uri + " is not on a trusted host of this application.");
            }
        }
        String applicationType = m.get("application_type") == null ? "web" : String.valueOf(m.get("application_type")).toLowerCase(Locale.ROOT);
        if (!Set.of("web", "native").contains(applicationType)) {
            throw new RegistrationException(400, "invalid_client_metadata", "application_type must be web or native.");
        }
        String authMethod = m.get("token_endpoint_auth_method") == null ? "client_secret_basic"
                : String.valueOf(m.get("token_endpoint_auth_method")).toLowerCase(Locale.ROOT);
        if (!AUTH_METHODS.contains(authMethod)) {
            throw new RegistrationException(400, "invalid_client_metadata",
                    "token_endpoint_auth_method must be one of " + new java.util.TreeSet<>(AUTH_METHODS) + ".");
        }
        if ("none".equals(authMethod) && !code) {
            throw new RegistrationException(400, "invalid_client_metadata", "A client without credentials can only use authorization_code.");
        }
        String jwksUri = m.get("jwks_uri") == null ? null : String.valueOf(m.get("jwks_uri"));
        Map<String, Object> jwks = m.get("jwks") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        if (jwksUri != null && jwks != null) {
            throw new RegistrationException(400, "invalid_client_metadata", "Send jwks_uri or jwks, not both.");
        }
        if ("private_key_jwt".equals(authMethod) && jwksUri == null && jwks == null) {
            throw new RegistrationException(400, "invalid_client_metadata", "private_key_jwt needs jwks_uri or jwks.");
        }
        List<String> scopes;
        if (m.get("scope") == null) {
            scopes = new ArrayList<>(policy.allowedScopes());
        } else {
            scopes = new ArrayList<>(new LinkedHashSet<>(List.of(String.valueOf(m.get("scope")).trim().split("\\s+"))));
            scopes.removeIf(String::isBlank);
            for (String scope : scopes) {
                if (!policy.allowedScopes().contains(scope)) {
                    throw new RegistrationException(400, "invalid_client_metadata",
                            "scope " + scope + " is not allowed here (allowed: " + String.join(" ", policy.allowedScopes()) + ").");
                }
            }
        }
        String type = "none".equals(authMethod) ? ("native".equals(applicationType) ? "native" : "spa") : code ? "web" : "m2m";
        Map<String, Object> echo = new LinkedHashMap<>();
        for (String key : List.of("client_name", "client_uri", "logo_uri", "policy_uri", "tos_uri", "software_id", "software_version")) {
            Object value = m.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (text.length() > 500 || text.chars().anyMatch(Character::isISOControl)) {
                    throw new RegistrationException(400, "invalid_client_metadata", key + " is too long or has control characters.");
                }
                if (key.endsWith("_uri") && !text.startsWith("https://")) {
                    throw new RegistrationException(400, "invalid_client_metadata", key + " must be an https:// address.");
                }
                echo.put(key, text);
            }
        }
        if (m.get("contacts") != null) {
            List<String> contacts = metaStrings(m.get("contacts"), "contacts");
            if (contacts.size() > 10 || contacts.stream().anyMatch(c -> c.length() > 254 || !c.contains("@"))) {
                throw new RegistrationException(400, "invalid_client_metadata", "contacts must be up to 10 email addresses.");
            }
            echo.put("contacts", contacts);
        }
        echo.put("application_type", applicationType);
        echo.put("redirect_uris", redirects);
        if (!postLogout.isEmpty()) echo.put("post_logout_redirect_uris", postLogout);
        echo.put("grant_types", grants);
        echo.put("response_types", responseTypes);
        echo.put("token_endpoint_auth_method", authMethod);
        echo.put("scope", String.join(" ", scopes));
        if (jwksUri != null) echo.put("jwks_uri", jwksUri);
        if (jwks != null) echo.put("jwks", jwks);
        return new Requested(type, authMethod, grants, scopes, redirects, postLogout, jwksUri, jwks, echo);
    }

    static boolean trusted(String uri, List<String> hosts) {
        String host;
        try {
            host = URI.create(uri).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            // A native app scheme (com.example.app:/cb) has no host: trusted hosts do not cover it.
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String trusted : hosts) {
            if (trusted.startsWith("*.") ? h.endsWith(trusted.substring(1)) : h.equals(trusted)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> response(String clientId, String secret, Map<String, Object> metadata, String registrationBase) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("client_id", clientId);
        if (secret != null) {
            out.put("client_secret", secret);
            out.put("client_secret_expires_at", 0);
        }
        OAuthClient client = RlsScope.asPlatform(() -> clients.findByClientId(clientId)).orElse(null);
        if (client != null && client.getCreatedAt() != null) {
            out.put("client_id_issued_at", client.getCreatedAt().getEpochSecond());
        }
        out.put("registration_client_uri", registrationBase + "/" + clientId);
        out.putAll(metadata);
        return out;
    }

    private OAuthClient authorized(String clientId, String bearer) {
        Optional<OAuthClient> client = RlsScope.asPlatform(() -> clients.findByClientId(clientId == null ? "" : clientId));
        if (bearer == null || client.isEmpty() || !client.get().isDynamicallyRegistered() || client.get().getRegistrationTokenHash() == null
                || !MessageDigest.isEqual(client.get().getRegistrationTokenHash().getBytes(StandardCharsets.UTF_8),
                        sha256(bearer).getBytes(StandardCharsets.UTF_8))) {
            // RFC 7592: an unknown client and a wrong token look the same.
            throw new RegistrationException(401, "invalid_token", "The registration access token is not valid for this client.");
        }
        return client.get();
    }

    private String rotate(OAuthClient client) {
        String rotated = TOKEN_PREFIX + randomToken();
        RlsScope.runAsPlatform(() -> clients.findById(client.getId()).ifPresent(c -> {
            c.setRegistrationTokenHash(sha256(rotated));
            clients.save(c);
        }));
        return rotated;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadataOf(OAuthClient client) {
        try {
            return client.getRegistrationMetadata() == null ? new LinkedHashMap<>() : JSON.readValue(client.getRegistrationMetadata(), LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** The token's id and application, when it can still register a client. */
    private Optional<Map.Entry<UUID, UUID>> usableToken(String value) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT id, application_id FROM platform.client_registration_token WHERE token_hash = ? AND revoked_at IS NULL "
                        + "AND uses < max_uses AND expires_at > now()",
                (rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), sha256(value)).stream().findFirst());
    }

    private boolean consume(UUID tokenId) {
        return RlsScope.asPlatform(() -> jdbc.update(
                "UPDATE platform.client_registration_token SET uses = uses + 1 WHERE id = ? AND uses < max_uses AND revoked_at IS NULL AND expires_at > now()",
                tokenId)) == 1;
    }

    private Optional<Policy> openPolicy(UUID tenantId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.application_client_registration WHERE tenant_id = ? AND mode = 'open'", this::policyRow, tenantId)
                .stream().findFirst());
    }

    public UUID tenantOf(UUID applicationId) {
        return RlsScope.asPlatform(() -> jdbc.queryForObject(
                "SELECT tenant_id FROM platform.application WHERE id = ?", UUID.class, applicationId));
    }

    private Policy policyRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Policy(rs.getObject("application_id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("mode"),
                list(rs.getString("allowed_scopes")), list(rs.getString("allowed_grant_types")), list(rs.getString("trusted_hosts")),
                rs.getInt("max_clients"), rs.getInt("unused_days"));
    }

    private InitialToken tokenRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        java.sql.Timestamp revoked = rs.getTimestamp("revoked_at");
        return new InitialToken(rs.getObject("id", UUID.class), rs.getString("label"), rs.getInt("max_uses"), rs.getInt("uses"),
                rs.getTimestamp("expires_at").toInstant(), revoked == null ? null : revoked.toInstant(), rs.getTimestamp("created_at").toInstant());
    }

    private static List<String> list(String json) {
        try {
            return json == null ? List.of() : JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> strings(Object raw, String field) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(field + " must be a list.");
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String s = item == null ? "" : String.valueOf(item).trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static List<String> metaStrings(Object raw, String field) {
        if (!(raw instanceof List<?> list)) {
            throw new RegistrationException(400, "invalid_client_metadata", field + " must be a JSON array of strings.");
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s) || s.isBlank()) {
                throw new RegistrationException(400, "invalid_client_metadata", field + " must be a JSON array of strings.");
            }
            if (!out.contains(s.trim())) out.add(s.trim());
        }
        if (out.size() > 20) {
            throw new RegistrationException(400, "invalid_client_metadata", field + ": at most 20 values.");
        }
        return out;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String randomToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
