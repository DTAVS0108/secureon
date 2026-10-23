package com.secureone.auth.application;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which application pages may receive password-reset, magic-link and similar tokens (Integration › client URLs).
 * A token sent anywhere else would hand the account to whoever runs that page, so the page must be served over
 * HTTPS (plain HTTP only on this computer, for development) from a site the application already trusts: the origin
 * of one of its OAuth clients' redirect or post-logout addresses.
 */
public final class ClientUrlPolicy {

    private ClientUrlPolicy() {}

    /** Why {@code url} may not receive tokens, or null when it may. */
    public static String problem(String url, Set<String> trustedOrigins) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return "is not a valid address";
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!uri.isAbsolute() || uri.getHost() == null) {
            return "must be a full address, such as https://app.example.com/reset";
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            return "must not contain a user name or a # part";
        }
        if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopback(uri.getHost()))) {
            return "must use https:// (http:// only for localhost)";
        }
        if (!trustedOrigins.contains(origin(uri))) {
            return "must be on a site this application's OAuth clients redirect to ("
                    + (trustedOrigins.isEmpty() ? "add an OAuth client with a redirect URI first" : String.join(", ", trustedOrigins))
                    + ")";
        }
        return null;
    }

    /** Origins (scheme://host[:port]) of the given redirect addresses; unparsable ones are skipped. */
    public static Set<String> origins(Collection<String> uris) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : uris) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                URI uri = new URI(raw.trim());
                if (uri.getScheme() != null && uri.getHost() != null) {
                    out.add(origin(uri));
                }
            } catch (Exception ignored) {
                // Not an address a token could be sent to.
            }
        }
        return out;
    }

    private static String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    private static boolean isLoopback(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || "127.0.0.1".equals(h) || "[::1]".equals(h) || "::1".equals(h);
    }
}
