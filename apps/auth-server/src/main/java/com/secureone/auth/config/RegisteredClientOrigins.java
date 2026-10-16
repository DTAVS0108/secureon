package com.secureone.auth.config;

import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.tenant.RlsScope;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Browser origins of the apps registered here: scheme, host and port of every http(s) redirect and
 * post-logout URI of an active OAuth client that signs users in (not machine-to-machine clients).
 *
 * <p>CORS allows them in addition to {@code SECUREONE_CORS_ALLOWED_ORIGINS}, so a single-page or
 * native-UI app works as soon as its client is registered, without changing the server environment.
 * Registering a redirect URI already trusts that origin with authorization codes; this adds the
 * browser calls the same app makes (token, userinfo, the app's public sign-in APIs).
 *
 * <p>Cached for {@link #TTL_MILLIS}; changes through the admin API refresh it immediately on this node.
 */
@Component
public class RegisteredClientOrigins {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientOrigins.class);
    static final long TTL_MILLIS = 30_000;

    private final OAuthClientRepository clients;
    private volatile Set<String> origins = Set.of();
    private volatile long loadedAt;

    public RegisteredClientOrigins(OAuthClientRepository clients) {
        this.clients = clients;
    }

    public boolean isRegistered(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return current().contains(origin.toLowerCase(Locale.ROOT));
    }

    /** Called after a client is created, changed or deleted. */
    public void invalidate() {
        loadedAt = 0;
    }

    private Set<String> current() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > TTL_MILLIS) {
            synchronized (this) {
                if (now - loadedAt > TTL_MILLIS) {
                    try {
                        origins = load();
                    } catch (RuntimeException e) {
                        // Keep serving the last known set; the env list and localhost still work.
                        log.warn("Could not refresh registered client origins: {}", e.getMessage());
                    }
                    loadedAt = now;
                }
            }
        }
        return origins;
    }

    private Set<String> load() {
        List<OAuthClient> all = RlsScope.asPlatform(clients::findAll);
        Set<String> out = new HashSet<>();
        for (OAuthClient client : all) {
            if (!"ACTIVE".equalsIgnoreCase(client.getStatus()) || "m2m".equalsIgnoreCase(client.getType())) {
                continue;
            }
            addOrigins(out, client.getRedirectUris());
            addOrigins(out, client.getPostLogoutRedirectUris());
        }
        return Set.copyOf(out);
    }

    private static void addOrigins(Set<String> out, List<String> uris) {
        if (uris == null) {
            return;
        }
        for (String uri : uris) {
            String origin = originOf(uri);
            if (origin != null) {
                out.add(origin);
            }
        }
    }

    /** {@code https://app.example.com:8443} for an http(s) URI; null for app schemes and malformed values. */
    static String originOf(String uri) {
        try {
            URI parsed = URI.create(uri.trim());
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || parsed.getHost() == null) {
                return null;
            }
            int port = parsed.getPort();
            boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
            return scheme + "://" + parsed.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
