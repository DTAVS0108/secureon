package com.secureone.auth.admin.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates OAuth redirect and post-logout URIs (RFC 6749 3.1.2, RFC 8252, OAuth 2.1): absolute,
 * no fragment, no wildcards; https with a host (plain http only on the loopback interface, RFC 8252 7.3), or a
 * reverse-domain private-use scheme for native apps (for example {@code com.example.app:/callback}).
 */
public final class RedirectUriValidator {

    private static final Set<String> FORBIDDEN_SCHEMES =
            Set.of("javascript", "data", "vbscript", "file", "about", "blob", "filesystem");

    static final String PLAIN_HTTP = "use https; plain http is only for localhost, 127.0.0.1 or [::1].";

    private RedirectUriValidator() {}

    static boolean isLoopback(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.endsWith(".localhost") || h.equals("127.0.0.1")
                || h.equals("[::1]") || h.equals("::1");
    }

    /** Returns the trimmed, de-duplicated URIs or throws {@link IllegalArgumentException} naming the bad one. */
    static List<String> validate(List<String> uris, String field) {
        return validate(uris, field, List.of());
    }

    /**
     * As {@link #validate(List, String)}, for an update: a plain-http URI the client already had is kept (clients
     * registered before the loopback-only rule still save); new ones follow it.
     */
    static List<String> validate(List<String> uris, String field, java.util.Collection<String> existing) {
        if (uris == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : uris) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String uri = raw.trim();
            String problem = problem(uri);
            if (PLAIN_HTTP.equals(problem) && existing != null && existing.contains(uri)) {
                problem = null;
            }
            if (problem != null) {
                throw new IllegalArgumentException(field + " '" + uri + "' is not allowed: " + problem);
            }
            out.add(uri);
        }
        return new ArrayList<>(out);
    }

    public static String problem(String uri) {
        if (uri.contains("*")) {
            return "wildcards are not supported; register each URI exactly.";
        }
        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException ex) {
            return "not a valid URI.";
        }
        if (!parsed.isAbsolute() || parsed.getScheme() == null) {
            return "must be an absolute URI with a scheme.";
        }
        if (parsed.getRawFragment() != null) {
            return "must not contain a fragment (#).";
        }
        String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
        if (FORBIDDEN_SCHEMES.contains(scheme)) {
            return "the " + scheme + ": scheme is not allowed.";
        }
        if (scheme.equals("http") || scheme.equals("https")) {
            if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                return "must include a host.";
            }
            return scheme.equals("http") && !isLoopback(parsed.getHost()) ? PLAIN_HTTP : null;
        }
        // Native apps: private-use scheme in reverse-domain form (RFC 8252 7.1).
        return scheme.contains(".") ? null : "use https, http, or a reverse-domain app scheme such as com.example.app.";
    }
}
