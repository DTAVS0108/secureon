package com.secureone.auth.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Issuers of SecureOne: the shared one (SECUREONE_ISSUER_URL), each tenant's own ({@code <shared>/t/<slug>}) and
 * each tenant's verified custom domains ({@code https://<domain>}). A request to a tenant issuer is served as that
 * tenant's: discovery names its endpoints, tokens carry it in "iss", only the tenant's clients are served.
 */
@Service
public class TenantIssuers {

    /** Request attribute: the {@link Target} a request was addressed to (absent: the shared issuer). */
    public static final String ATTRIBUTE = TenantIssuers.class.getName() + ".TARGET";

    public static final String MODE_SHARED = "shared";
    /** The tenant's clients are served on its own issuers only. */
    public static final String MODE_TENANT = "tenant";

    private static final Pattern PATH = Pattern.compile("^/t/([a-z0-9][a-z0-9-]{0,62})(/.*)?$");
    private static final Duration CACHE = Duration.ofSeconds(30);

    /**
     * Where a request went: the tenant, the issuer it addressed, and the path prefix it carried ({@code /t/slug},
     * or "" on a custom domain, which serves every path).
     */
    public record Target(UUID tenantId, String slug, String issuer, String prefix) {}

    private record TenantRow(UUID id, String slug, String status, String issuerMode) {}

    private record Cached<T>(T value, Instant until) {}

    private final JdbcTemplate jdbc;
    private final String sharedIssuer;
    private final Map<String, Cached<Optional<TenantRow>>> bySlug = new ConcurrentHashMap<>();
    private final Map<UUID, Cached<Optional<TenantRow>>> byId = new ConcurrentHashMap<>();
    private volatile Cached<Map<String, UUID>> domains;

    public TenantIssuers(
            JdbcTemplate jdbc,
            @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:9000}") String sharedIssuer) {
        this.jdbc = jdbc;
        this.sharedIssuer = sharedIssuer.endsWith("/") ? sharedIssuer.substring(0, sharedIssuer.length() - 1) : sharedIssuer;
    }

    public String sharedIssuer() {
        return sharedIssuer;
    }

    public String pathIssuer(String slug) {
        return sharedIssuer + "/t/" + slug;
    }

    /**
     * {@code https://<domain>} (http when the shared issuer is http). With no port given, the shared issuer's: the
     * reverse proxy serves the domain where it serves SecureOne.
     */
    public String domainIssuer(String domain, int port) {
        boolean http = sharedIssuer.startsWith("http://");
        if (port <= 0) {
            port = java.net.URI.create(sharedIssuer).getPort();
        }
        String scheme = http ? "http" : "https";
        boolean defaultPort = port <= 0 || (http && port == 80) || (!http && port == 443);
        return scheme + "://" + domain + (defaultPort ? "" : ":" + port);
    }

    /** The tenant issuer a request addresses (by path or by host), if any; empty when the tenant is unknown. */
    public Optional<Target> resolve(HttpServletRequest request) {
        String host = request.getServerName() == null ? "" : request.getServerName().toLowerCase(Locale.ROOT);
        UUID domainTenant = verifiedDomains().get(host);
        if (domainTenant != null) {
            return tenant(domainTenant).filter(t -> "ACTIVE".equalsIgnoreCase(t.status()))
                    .map(t -> new Target(t.id(), t.slug(), domainIssuer(host, request.getServerPort()), ""));
        }
        Matcher m = PATH.matcher(request.getRequestURI());
        if (!m.matches()) {
            return Optional.empty();
        }
        return tenant(m.group(1)).filter(t -> "ACTIVE".equalsIgnoreCase(t.status()))
                .map(t -> new Target(t.id(), t.slug(), pathIssuer(t.slug()), "/t/" + t.slug()));
    }

    /** Whether the path starts with a tenant issuer prefix ({@code /t/<slug>}), known tenant or not. */
    public static boolean hasTenantPrefix(String path) {
        return path != null && PATH.matcher(path).matches();
    }

    /** The prefix a request carried before it was mapped to the shared paths ("" for none). */
    public static String prefix(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof Target target ? target.prefix() : "";
    }

    /** Whether "iss" names one of SecureOne's issuers: the shared one, a tenant's, or a verified domain's. */
    public boolean isOwnIssuer(String issuer) {
        if (issuer == null) {
            return false;
        }
        if (issuer.equals(sharedIssuer)) {
            return true;
        }
        String prefix = sharedIssuer + "/t/";
        if (issuer.startsWith(prefix)) {
            String slug = issuer.substring(prefix.length());
            return tenant(slug).map(t -> t.slug().equals(slug)).orElse(false);
        }
        try {
            java.net.URI uri = java.net.URI.create(issuer);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPath() != null && (uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    && verifiedDomains().containsKey(host) && issuer.equals(domainIssuer(host, uri.getPort()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Why the issuer this request addressed may not serve a client of {@code clientTenantId}, if it may not: a
     * tenant issuer serves its own tenant's clients only, and the shared issuer refuses tenants that use their
     * own issuers only.
     */
    public Optional<String> refusal(HttpServletRequest request, UUID clientTenantId) {
        Object target = request == null ? null : request.getAttribute(ATTRIBUTE);
        if (target instanceof Target t) {
            return t.tenantId().equals(clientTenantId)
                    ? Optional.empty()
                    : Optional.of("This client belongs to another organization: use its own issuer.");
        }
        if (clientTenantId != null && MODE_TENANT.equals(issuerMode(clientTenantId))) {
            return Optional.of("This organization uses its own issuer: " + tenant(clientTenantId).map(r -> pathIssuer(r.slug())).orElse("")
                    + " (or its custom domain).");
        }
        return Optional.empty();
    }

    public String issuerMode(UUID tenantId) {
        return tenant(tenantId).map(TenantRow::issuerMode).orElse(MODE_SHARED);
    }

    /** Every issuer the tenant's clients may use, the preferred one first. */
    public List<String> issuersOf(UUID tenantId) {
        Optional<TenantRow> row = tenant(tenantId);
        if (row.isEmpty()) {
            return List.of(sharedIssuer);
        }
        List<String> out = new java.util.ArrayList<>();
        verifiedDomains().forEach((domain, owner) -> {
            if (owner.equals(tenantId)) out.add(domainIssuer(domain, -1));
        });
        java.util.Collections.sort(out);
        out.add(pathIssuer(row.get().slug()));
        if (!MODE_TENANT.equals(row.get().issuerMode())) {
            out.add(sharedIssuer);
        }
        return out;
    }

    /**
     * The issuer an application's tokens carry when no request says (logout tokens sent later): the shared one,
     * or the tenant's own first issuer when it uses its own issuers only.
     */
    public String preferredIssuerOfApplication(UUID applicationId) {
        if (applicationId == null) {
            return sharedIssuer;
        }
        UUID tenantId = RlsScope.asPlatform(() -> jdbc.query(
                "SELECT tenant_id FROM platform.application WHERE id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), applicationId).stream().findFirst().orElse(null));
        if (tenantId == null || !MODE_TENANT.equals(issuerMode(tenantId))) {
            return sharedIssuer;
        }
        return issuersOf(tenantId).get(0);
    }

    /** Forgets cached tenants and domains (after a change). */
    public void invalidate() {
        bySlug.clear();
        byId.clear();
        domains = null;
    }

    private Optional<TenantRow> tenant(String slug) {
        Cached<Optional<TenantRow>> cached = bySlug.get(slug);
        if (cached != null && cached.until().isAfter(Instant.now())) {
            return cached.value();
        }
        Optional<TenantRow> row = RlsScope.asPlatform(() -> jdbc.query(
                "SELECT id, slug, status, issuer_mode FROM platform.tenant WHERE slug = ?",
                (rs, i) -> new TenantRow(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("status"),
                        rs.getString("issuer_mode")),
                slug).stream().findFirst());
        bySlug.put(slug, new Cached<>(row, Instant.now().plus(CACHE)));
        return row;
    }

    private Optional<TenantRow> tenant(UUID id) {
        Cached<Optional<TenantRow>> cached = byId.get(id);
        if (cached != null && cached.until().isAfter(Instant.now())) {
            return cached.value();
        }
        Optional<TenantRow> row = RlsScope.asPlatform(() -> jdbc.query(
                "SELECT id, slug, status, issuer_mode FROM platform.tenant WHERE id = ?",
                (rs, i) -> new TenantRow(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("status"),
                        rs.getString("issuer_mode")),
                id).stream().findFirst());
        byId.put(id, new Cached<>(row, Instant.now().plus(CACHE)));
        return row;
    }

    /** Verified custom domains (lower case) and their tenants. */
    Map<String, UUID> verifiedDomains() {
        Cached<Map<String, UUID>> cached = domains;
        if (cached != null && cached.until().isAfter(Instant.now())) {
            return cached.value();
        }
        Map<String, UUID> map = new java.util.HashMap<>();
        RlsScope.runAsPlatform(() -> jdbc.query(
                "SELECT lower(domain) AS domain, tenant_id FROM platform.tenant_domain WHERE status = 'VERIFIED'",
                rs -> {
                    map.put(rs.getString("domain"), rs.getObject("tenant_id", UUID.class));
                }));
        Map<String, UUID> value = Map.copyOf(map);
        domains = new Cached<>(value, Instant.now().plus(CACHE));
        return value;
    }
}
