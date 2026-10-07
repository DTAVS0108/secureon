package com.secureone.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence for cookie-authenticated endpoints that cannot carry a CSRF token (hosted pages and
 * integrated apps call them with fetch). A state-changing request that carries cookies must come
 * from this server's own origin or an allowed CORS origin, judged by {@code Origin}, else {@code Referer}.
 * Requests without either header (non-browser clients) are not affected.
 *
 * <p>Registered as a servlet filter ahead of Spring Security so form login is covered too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class SessionOriginGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionOriginGuardFilter.class);

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Endpoints that legitimately receive cross-origin POSTs or never use cookies. */
    private static final List<String> EXEMPT_PREFIXES = List.of("/api/v1/auth/federation/", "/scim/v2/");

    private final CorsConfigurationSource cors;
    private final String publicOrigin;

    public SessionOriginGuardFilter(
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
            @Value("${secureone.public-base-url:}") String publicBaseUrl) {
        this.cors = cors;
        this.publicOrigin = originOf(publicBaseUrl);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        if (request.getCookies() == null || request.getCookies().length == 0) {
            return true;
        }
        String path = request.getRequestURI();
        return EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            origin = originOf(request.getHeader("Referer"));
        }
        if (origin == null || isAllowed(origin, request)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Blocked cross-origin {} {} from origin {}", request.getMethod(), request.getRequestURI(), origin);
        // Written directly: sendError would forward to /error, which Spring Security answers with 401.
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"Cross-origin request blocked.\"}");
    }

    boolean isAllowed(String origin, HttpServletRequest request) {
        if ("null".equals(origin)) {
            // Sandboxed frames and some redirects; never trusted with cookies.
            return false;
        }
        String normalized = origin.toLowerCase(Locale.ROOT);
        if (normalized.equals(ownOrigin(request)) || normalized.equals(publicOrigin)) {
            return true;
        }
        CorsConfiguration config = cors.getCorsConfiguration(request);
        return config != null && config.checkOrigin(origin) != null;
    }

    private static String ownOrigin(HttpServletRequest request) {
        String scheme = request.getScheme().toLowerCase(Locale.ROOT);
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    /** scheme://host[:port] of a URL, lower-cased; null when absent or unparsable. */
    static String originOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String base = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPort() >= 0 ? base + ":" + uri.getPort() : base;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
