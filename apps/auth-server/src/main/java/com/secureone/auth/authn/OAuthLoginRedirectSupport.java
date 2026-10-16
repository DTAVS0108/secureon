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

    /** The current request as a relative URL (path + query), so it always passes {@link #isSafeContinue}. */
    public static String buildAuthorizeUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + query;
    }

    public static String loginPageWithContinue(String authorizeUrl) {
        return "/login.html?"
                + CONTINUE_PARAM
                + "="
                + URLEncoder.encode(authorizeUrl, StandardCharsets.UTF_8);
    }

    /** OAuth endpoints a browser may be sent back to after signing in. */
    private static final java.util.List<String> CONTINUE_PATHS =
            java.util.List.of("/oauth2/authorize", "/oauth2/device_verification");

    public static boolean isAuthorizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String path = URI.create(url.trim()).getPath();
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
