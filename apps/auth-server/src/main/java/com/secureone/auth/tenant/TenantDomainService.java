package com.secureone.auth.tenant;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * A tenant's custom domains (login.example.com). A domain is served as the tenant's once verified, by either
 * proof of control: a DNS TXT record {@code _secureone-challenge.<domain>} holding the token, or the domain
 * already pointing at SecureOne, which then answers {@code /.well-known/secureone-domain-verification} on it
 * with the token. TLS for the domain is the reverse proxy's (it can ask {@link #isVerified} before issuing a
 * certificate on demand).
 */
@Service
public class TenantDomainService {

    public static final String WELL_KNOWN = "/.well-known/secureone-domain-verification";
    public static final String TXT_PREFIX = "_secureone-challenge.";
    public static final String TXT_VALUE_PREFIX = "secureone-domain-verification=";
    static final int MAX_PER_TENANT = 10;

    private static final Pattern HOST = Pattern.compile(
            "^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Domain(
            UUID id, UUID tenantId, String domain, String status, String verificationToken, Instant verifiedAt,
            Instant lastCheckedAt, String lastError, Instant createdAt) {}

    private final JdbcTemplate jdbc;
    private final TenantIssuers issuers;
    private final boolean production;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public TenantDomainService(
            JdbcTemplate jdbc,
            TenantIssuers issuers,
            com.secureone.auth.config.SecureOneEnvironmentProperties environment) {
        this.jdbc = jdbc;
        this.issuers = issuers;
        this.production = environment.isProduction();
    }

    public List<Domain> list(UUID tenantId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.tenant_domain WHERE tenant_id = ? ORDER BY created_at", this::row, tenantId));
    }

    public Domain require(UUID tenantId, UUID id) {
        return list(tenantId).stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new com.secureone.auth.admin.ResourceNotFoundException("Domain not found: " + id));
    }

    public Domain add(UUID tenantId, String raw) {
        String domain = normalize(raw);
        String shared = hostOf(issuers.sharedIssuer());
        if (domain.equals(shared)) {
            throw new IllegalArgumentException("That is SecureOne's own address.");
        }
        if (list(tenantId).size() >= MAX_PER_TENANT) {
            throw new IllegalArgumentException("A tenant can have at most " + MAX_PER_TENANT + " custom domains.");
        }
        Boolean taken = RlsScope.asPlatform(() -> jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM platform.tenant_domain WHERE lower(domain) = ?)", Boolean.class, domain));
        if (Boolean.TRUE.equals(taken)) {
            throw new com.secureone.auth.admin.ConflictException("This domain is already registered.");
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        UUID id = UUID.randomUUID();
        String token = HexFormat.of().formatHex(bytes);
        RlsScope.runAsPlatform(() -> jdbc.update(
                "INSERT INTO platform.tenant_domain (id, tenant_id, domain, status, verification_token) VALUES (?, ?, ?, 'PENDING', ?)",
                id, tenantId, domain, token));
        return require(tenantId, id);
    }

    /** Checks the DNS record, then the domain's answer; VERIFIED on either, else the reason is kept. */
    public Domain verify(UUID tenantId, UUID id) {
        Domain d = require(tenantId, id);
        String problem = null;
        boolean ok = dnsHasToken(d.domain(), d.verificationToken());
        if (!ok) {
            Optional<String> http = httpProblem(d.domain(), d.verificationToken());
            ok = http.isEmpty();
            problem = ok ? null : "No TXT record " + TXT_PREFIX + d.domain() + " with the token, and " + http.get();
        }
        String error = problem;
        boolean verified = ok;
        RlsScope.runAsPlatform(() -> jdbc.update(
                "UPDATE platform.tenant_domain SET status = ?, verified_at = CASE WHEN ? THEN COALESCE(verified_at, now()) ELSE verified_at END, "
                        + "last_checked_at = now(), last_error = ? WHERE id = ?",
                verified ? "VERIFIED" : d.status(), verified, error == null ? null : truncate(error), id));
        issuers.invalidate();
        return require(tenantId, id);
    }

    public Domain delete(UUID tenantId, UUID id) {
        Domain d = require(tenantId, id);
        RlsScope.runAsPlatform(() -> jdbc.update("DELETE FROM platform.tenant_domain WHERE id = ?", id));
        issuers.invalidate();
        return d;
    }

    /** "shared" (both the shared issuer and the tenant's) or "tenant" (the tenant's own issuers only). */
    public void setIssuerMode(UUID tenantId, String raw) {
        String mode = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!TenantIssuers.MODE_SHARED.equals(mode) && !TenantIssuers.MODE_TENANT.equals(mode)) {
            throw new IllegalArgumentException("issuerMode must be 'shared' or 'tenant'.");
        }
        RlsScope.runAsPlatform(() -> jdbc.update("UPDATE platform.tenant SET issuer_mode = ? WHERE id = ?", mode, tenantId));
        issuers.invalidate();
    }

    /** The token a host answers on {@link #WELL_KNOWN}: a registered domain's, while it waits or once verified. */
    public Optional<String> tokenFor(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT verification_token FROM platform.tenant_domain WHERE lower(domain) = ?",
                (rs, i) -> rs.getString(1), h).stream().findFirst());
    }

    public boolean isVerified(String host) {
        return host != null && issuers.verifiedDomains().containsKey(host.trim().toLowerCase(Locale.ROOT));
    }

    /** How to prove control of the domain, for the console. */
    public Map<String, Object> view(Domain d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.id());
        out.put("domain", d.domain());
        out.put("status", d.status().toLowerCase(Locale.ROOT));
        out.put("issuer", issuers.domainIssuer(d.domain(), -1));
        out.put("txtRecordName", TXT_PREFIX + d.domain());
        out.put("txtRecordValue", TXT_VALUE_PREFIX + d.verificationToken());
        out.put("httpCheckUrl", checkUrl(d.domain()));
        out.put("verifiedAt", d.verifiedAt());
        out.put("lastCheckedAt", d.lastCheckedAt());
        out.put("lastError", d.lastError());
        return out;
    }

    static String normalize(String raw) {
        String domain = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (!HOST.matcher(domain).matches()) {
            throw new IllegalArgumentException("Enter a host name such as login.example.com (no scheme, port or path).");
        }
        return domain;
    }

    private boolean dnsHasToken(String domain, String token) {
        java.util.Hashtable<String, String> env = new java.util.Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            javax.naming.directory.DirContext dns = new javax.naming.directory.InitialDirContext(env);
            try {
                javax.naming.directory.Attribute txt =
                        dns.getAttributes(TXT_PREFIX + domain, new String[] {"TXT"}).get("TXT");
                if (txt == null) {
                    return false;
                }
                for (int i = 0; i < txt.size(); i++) {
                    String value = String.valueOf(txt.get(i)).replace("\"", "").trim();
                    if (value.equals(TXT_VALUE_PREFIX + token)) {
                        return true;
                    }
                }
                return false;
            } finally {
                dns.close();
            }
        } catch (javax.naming.NamingException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Why the domain does not answer the token (empty: it does). */
    private Optional<String> httpProblem(String domain, String token) {
        String url = checkUrl(domain);
        try {
            if (production) {
                for (InetAddress address : InetAddress.getAllByName(domain)) {
                    if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                            || address.isAnyLocalAddress()) {
                        return Optional.of(domain + " points at a private address.");
                    }
                }
            }
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && response.body().trim().equals(token)) {
                return Optional.empty();
            }
            return Optional.of(url + " answered " + response.statusCode() + " without the token (does the domain point at SecureOne?).");
        } catch (java.net.UnknownHostException e) {
            return Optional.of(domain + " does not resolve yet.");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.of(url + " could not be reached.");
        }
    }

    /** The verification URL on the domain: the shared issuer's scheme and port (so a test setup on a port works). */
    private String checkUrl(String domain) {
        URI shared = URI.create(issuers.sharedIssuer());
        String scheme = shared.getScheme() == null ? "https" : shared.getScheme();
        int port = shared.getPort();
        return scheme + "://" + domain + (port > 0 ? ":" + port : "") + WELL_KNOWN;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String truncate(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private Domain row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        java.sql.Timestamp verified = rs.getTimestamp("verified_at");
        java.sql.Timestamp checked = rs.getTimestamp("last_checked_at");
        return new Domain(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("domain"),
                rs.getString("status"),
                rs.getString("verification_token"),
                verified == null ? null : verified.toInstant(),
                checked == null ? null : checked.toInstant(),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant());
    }
}
