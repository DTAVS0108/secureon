package com.secureone.auth.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.tenant.RlsScope;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * APIs (resource servers) of an application: a name, an identifier (the audience, e.g.
 * {@code https://api.example.com/orders}) and the scopes the API understands. An access token gets an API's
 * identifier in {@code aud} when it was granted one of the API's scopes, or when the client named the API with
 * the RFC 8707 {@code resource} parameter, so the API can refuse tokens issued for something else.
 */
@Service
public class ApplicationApiService {

    /** OIDC scopes; they describe the user, never an API. */
    static final Set<String> RESERVED_SCOPES = Set.of("openid", "profile", "email", "offline_access", "address", "phone");

    static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._/\\-]{0,99}");

    static final int MAX_SCOPES = 100;

    private static final TypeReference<List<Map<String, String>>> SCOPES = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final com.secureone.auth.cache.ConfigCache cache;

    public ApplicationApiService(JdbcTemplate jdbc, com.secureone.auth.cache.ConfigCache cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    public record Api(
            UUID id, UUID tenantId, UUID applicationId, String name, String identifier, List<Map<String, String>> scopes) {

        public Set<String> scopeValues() {
            Set<String> out = new LinkedHashSet<>();
            scopes.forEach(s -> out.add(s.get("value")));
            return out;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id.toString());
            out.put("applicationId", applicationId.toString());
            out.put("name", name);
            out.put("identifier", identifier);
            out.put("scopes", scopes);
            return out;
        }
    }

    public List<Api> list(UUID applicationId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM application_api WHERE application_id = ? ORDER BY lower(name), created_at",
                this::row, applicationId));
    }

    public Optional<Api> find(UUID applicationId, UUID id) {
        return list(applicationId).stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public Api create(UUID tenantId, UUID applicationId, Map<String, Object> body) {
        Api api = validated(UUID.randomUUID(), tenantId, applicationId, body);
        cache.changedAfterCommit();
        try {
            RlsScope.runAsPlatform(() -> jdbc.update(
                    "INSERT INTO application_api (id, tenant_id, application_id, name, identifier, scopes)"
                            + " VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                    api.id(), tenantId, applicationId, api.name(), api.identifier(), write(api.scopes())));
        } catch (DuplicateKeyException e) {
            throw identifierTaken(api.identifier());
        }
        return api;
    }

    public Api update(UUID tenantId, UUID applicationId, UUID id, Map<String, Object> body) {
        find(applicationId, id).orElseThrow(() -> notFound(id));
        Api api = validated(id, tenantId, applicationId, body);
        cache.changedAfterCommit();
        try {
            RlsScope.runAsPlatform(() -> jdbc.update(
                    "UPDATE application_api SET name = ?, identifier = ?, scopes = ?::jsonb, updated_at = now()"
                            + " WHERE id = ? AND application_id = ?",
                    api.name(), api.identifier(), write(api.scopes()), id, applicationId));
        } catch (DuplicateKeyException e) {
            throw identifierTaken(api.identifier());
        }
        return api;
    }

    public Api delete(UUID applicationId, UUID id) {
        Api api = find(applicationId, id).orElseThrow(() -> notFound(id));
        cache.changedAfterCommit();
        RlsScope.runAsPlatform(() -> jdbc.update(
                "DELETE FROM application_api WHERE id = ? AND application_id = ?", id, applicationId));
        return api;
    }

    /**
     * The audiences an access token of the application gets for these granted scopes. With {@code resource} (RFC
     * 8707) only that API, which must be one of the application's; {@link Optional#empty()} when it is not one
     * (the token request then fails with invalid_target).
     */
    public Optional<List<String>> audiences(UUID applicationId, Collection<String> grantedScopes, String resource) {
        // Asked for every access token: cached (ConfigCache); only read here, never changed.
        List<Api> apis = cache.get("application-apis", applicationId, () -> list(applicationId));
        if (resource != null && !resource.isBlank()) {
            return apis.stream()
                    .filter(a -> a.identifier().equalsIgnoreCase(resource.trim()))
                    .findFirst()
                    .map(a -> List.of(a.identifier()));
        }
        List<String> out = new ArrayList<>();
        for (Api api : apis) {
            if (api.scopeValues().stream().anyMatch(grantedScopes::contains)) {
                out.add(api.identifier());
            }
        }
        return Optional.of(out);
    }

    // ---- validation -------------------------------------------------------------------------------------------

    Api validated(UUID id, UUID tenantId, UUID applicationId, Map<String, Object> body) {
        String name = text(body.get("name"));
        if (name.isEmpty() || name.length() > 200) {
            throw bad("Give the API a name (up to 200 characters).");
        }
        String identifier = text(body.get("identifier"));
        String problem = identifierProblem(identifier);
        if (problem != null) {
            throw bad("Identifier '" + identifier + "': " + problem);
        }
        return new Api(id, tenantId, applicationId, name, identifier, scopes(body.get("scopes")));
    }

    static String identifierProblem(String identifier) {
        if (identifier.isEmpty() || identifier.length() > 512) {
            return "give an identifier of up to 512 characters, such as https://api.example.com.";
        }
        if (identifier.chars().anyMatch(Character::isWhitespace)) {
            return "must not contain spaces.";
        }
        URI uri;
        try {
            uri = new URI(identifier);
        } catch (URISyntaxException e) {
            return "not a valid URI.";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http") || scheme.equals("urn"))) {
            return "use an https:// URL or a urn: name (it is a name; nothing calls it).";
        }
        if (uri.getRawFragment() != null) {
            return "must not contain a fragment (#).";
        }
        return null;
    }

    static List<Map<String, String>> scopes(Object raw) {
        List<Map<String, String>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (raw instanceof Collection<?> list) {
            for (Object item : list) {
                String value;
                String description = "";
                if (item instanceof Map<?, ?> m) {
                    value = text(m.get("value"));
                    description = text(m.get("description"));
                } else {
                    value = text(item);
                }
                if (value.isEmpty()) {
                    continue;
                }
                if (!SCOPE.matcher(value).matches()) {
                    throw bad("Scope '" + value + "': use letters, digits and : . _ / - (up to 100 characters).");
                }
                if (RESERVED_SCOPES.contains(value.toLowerCase(Locale.ROOT))) {
                    throw bad("Scope '" + value + "' is an OpenID Connect scope; choose a name of the API's own.");
                }
                if (seen.add(value)) {
                    Map<String, String> scope = new LinkedHashMap<>();
                    scope.put("value", value);
                    scope.put("description", description.length() > 300 ? description.substring(0, 300) : description);
                    out.add(scope);
                }
            }
        }
        if (out.size() > MAX_SCOPES) {
            throw bad("An API can have up to " + MAX_SCOPES + " scopes.");
        }
        return out;
    }

    private static String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private Api row(ResultSet rs, int i) throws SQLException {
        List<Map<String, String>> scopes;
        try {
            scopes = json.readValue(rs.getString("scopes"), SCOPES);
        } catch (Exception e) {
            scopes = List.of();
        }
        return new Api(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getString("name"),
                rs.getString("identifier"),
                scopes);
    }

    private String write(List<Map<String, String>> scopes) {
        try {
            return json.writeValueAsString(scopes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "API not found: " + id);
    }

    private static ResponseStatusException identifierTaken(String identifier) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Another API already uses the identifier " + identifier + ". Identifiers are unique across the platform.");
    }
}
