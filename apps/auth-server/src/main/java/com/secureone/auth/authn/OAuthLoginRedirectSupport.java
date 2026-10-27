package com.secureone.auth.authn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/** Preserves OAuth authorize URLs across the hosted login page. */
public final class OAuthLoginRedirectSupport {

    public static final String CONTINUE_PARAM = "continue";
    public static final String CONTINUE_FIELD = "continue";

    private OAuthLoginRedirectSupport() {}

    /**
     * The current request as a relative URL (path + query), so it always passes {@link #isSafeContinue}. A request
     * to a tenant issuer keeps its {@code /t/<slug>} prefix, so the user comes back to the same issuer.
     */
    public static String buildAuthorizeUrl(HttpServletRequest request) {
        String path = com.secureone.auth.tenant.TenantIssuers.prefix(request) + request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + query;
    }

    /** Whether the request's {@code prompt} asks for {@code login} (OIDC: sign in again). */
    public static boolean asksPromptLogin(HttpServletRequest request) {
        String prompt = request.getParameter("prompt");
        return prompt != null && java.util.Arrays.asList(prompt.trim().split("\\s+")).contains("login");
    }

    /**
     * The request's URL with {@code login} taken out of {@code prompt} (and {@code prompt} dropped when nothing is
     * left): where the sign-in page sends the user back to after the sign-in prompt=login asked for, so that it
     * does not ask again.
     */
    public static String withoutPromptLogin(HttpServletRequest request) {
        StringBuilder query = new StringBuilder();
        for (var entry : request.getParameterMap().entrySet()) {
            for (String value : entry.getValue()) {
                String v = value;
                if ("prompt".equals(entry.getKey())) {
                    v = String.join(" ", java.util.Arrays.stream(value.trim().split("\\s+"))
                            .filter(p -> !"login".equals(p)).toList());
                    if (v.isBlank()) {
                        continue;
                    }
                }
                query.append(query.isEmpty() ? "" : "&")
                        .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        }
        return com.secureone.auth.tenant.TenantIssuers.prefix(request) + request.getRequestURI()
                + (query.isEmpty() ? "" : "?" + query);
    }

    /** The request as the sign-in page should return to it: prompt=login removed, everything else as sent. */
    public static HttpServletRequest withoutPromptLoginRequest(HttpServletRequest request) {
        if (!asksPromptLogin(request)) {
            return request;
        }
        String url = withoutPromptLogin(request);
        int q = url.indexOf('?');
        String query = q < 0 ? null : url.substring(q + 1);
        java.util.Map<String, String[]> params = new java.util.LinkedHashMap<>(request.getParameterMap());
        String[] prompt = params.get("prompt");
        String kept = prompt == null ? "" : String.join(" ", java.util.Arrays.stream(String.join(" ", prompt).trim().split("\\s+"))
                .filter(p -> !"login".equals(p)).toList());
        if (kept.isBlank()) {
            params.remove("prompt");
        } else {
            params.put("prompt", new String[] {kept});
        }
        return new jakarta.servlet.http.HttpServletRequestWrapper(request) {
            @Override
            public String getQueryString() {
                return query;
            }

            @Override
            public String getParameter(String name) {
                String[] values = params.get(name);
                return values == null || values.length == 0 ? null : values[0];
            }

            @Override
            public java.util.Map<String, String[]> getParameterMap() {
                return java.util.Collections.unmodifiableMap(params);
            }

            @Override
            public java.util.Enumeration<String> getParameterNames() {
                return java.util.Collections.enumeration(params.keySet());
            }

            @Override
            public String[] getParameterValues(String name) {
                return params.get(name);
            }
        };
    }

    public static String loginPageWithContinue(String authorizeUrl) {
        return "/login.html?"
                + CONTINUE_PARAM
                + "="
                + URLEncoder.encode(authorizeUrl, StandardCharsets.UTF_8);
    }

    /** Endpoints a browser may be sent back to after signing in (OAuth, and the SAML identity provider's resume). */
    private static final java.util.List<String> CONTINUE_PATHS =
            java.util.List.of("/oauth2/authorize", "/oauth2/device_verification", "/saml2/idp/resume");

    public static boolean isAuthorizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String path = URI.create(url.trim()).getPath();
            if (path != null && com.secureone.auth.tenant.TenantIssuers.hasTenantPrefix(path)) {
                // A tenant issuer's endpoint: /t/<slug>/oauth2/authorize.
                int rest = path.indexOf('/', 3);
                path = rest < 0 ? "" : path.substring(rest);
            }
            return path != null && CONTINUE_PATHS.stream().anyMatch(path::equals);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * A continue URL from the browser: an OAuth endpoint on this server only. Relative paths, or absolute
     * URLs to the host the request came in on; anything else (another host, {@code //host}, odd schemes)
     * would make sign-in an open redirect.
     */
    public static boolean isSafeContinue(HttpServletRequest request, String url) {
        if (!isAuthorizeUrl(url)) {
            return false;
        }
        String value = url.trim();
        if (value.contains("\\")) {
            return false;
        }
        URI uri = URI.create(value);
        if (uri.getScheme() == null && uri.getRawAuthority() == null) {
            return value.startsWith("/") && !value.startsWith("//");
        }
        boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        return http
                && uri.getHost() != null
                && uri.getHost().equalsIgnoreCase(request.getServerName())
                && effectivePort(uri) == request.getServerPort();
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public static String readContinueParam(HttpServletRequest request) {
        String raw = request.getParameter(CONTINUE_FIELD);
        if (raw == null || raw.isBlank()) {
            raw = request.getParameter(CONTINUE_PARAM);
        }
        return isSafeContinue(request, raw) ? raw.trim() : null;
    }

    public static String resolvePendingAuthorizeUrl(
            HttpServletRequest request, HttpServletResponse response, RequestCache requestCache) {
        String fromParam = readContinueParam(request);
        if (fromParam != null) {
            return fromParam;
        }
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return null;
        }
        String target = saved.getRedirectUrl();
        return isAuthorizeUrl(target) ? target : null;
    }

    public static String appendContinueToLoginUrl(String loginUrl, String authorizeUrl) {
        if (!isAuthorizeUrl(authorizeUrl)) {
            return loginUrl;
        }
        // Encode the authorize URL as one parameter value; strict "already encoded" parsing of the raw
        // value fails on its '=' characters (a failed sign-in during OAuth then returned 500).
        return loginUrl
                + (loginUrl.contains("?") ? "&" : "?")
                + CONTINUE_PARAM
                + "="
                + URLEncoder.encode(authorizeUrl, StandardCharsets.UTF_8);
    }

    public static RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(request -> isAuthorizeUrl(request.getRequestURI()));
        return cache;
    }
}
