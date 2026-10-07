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
 * no fragment, no wildcards; http(s) with a host, or a reverse-domain private-use scheme for
 * native apps (for example {@code com.example.app:/callback}).
 */
final class RedirectUriValidator {

    private static final Set<String> FORBIDDEN_SCHEMES =
            Set.of("javascript", "data", "vbscript", "file", "about", "blob", "filesystem");

    private RedirectUriValidator() {}

    /** Returns the trimmed, de-duplicated URIs or throws {@link IllegalArgumentException} naming the bad one. */
    static List<String> validate(List<String> uris, String field) {
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
            if (problem != null) {
                throw new IllegalArgumentException(field + " '" + uri + "' is not allowed: " + problem);
            }
            out.add(uri);
        }
        return new ArrayList<>(out);
    }

    static String problem(String uri) {
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
            return parsed.getHost() == null || parsed.getHost().isBlank() ? "must include a host." : null;
        }
        // Native apps: private-use scheme in reverse-domain form (RFC 8252 7.1).
        return scheme.contains(".") ? null : "use https, http, or a reverse-domain app scheme such as com.example.app.";
    }
}
