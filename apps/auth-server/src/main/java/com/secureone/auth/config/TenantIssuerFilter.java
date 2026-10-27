package com.secureone.auth.config;

import com.secureone.auth.tenant.TenantIssuers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tenant issuers. {@code /t/<slug>/<endpoint>} is served as {@code /<endpoint>} for that tenant (discovery, the
 * OAuth and OIDC endpoints), and every path on a tenant's verified custom domain is served as the tenant's. The
 * request carries the {@link TenantIssuers.Target} for the authorization server (issuer) and the client checks.
 * Runs first, so every later filter sees the endpoint's usual path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TenantIssuerFilter extends OncePerRequestFilter {

    /** What a tenant issuer path serves (RFC 8414 also allows the issuer path after the well-known name). */
    private static final List<String> ENDPOINTS = List.of(
            "/.well-known/openid-configuration", "/.well-known/oauth-authorization-server", "/oauth2/", "/userinfo",
            "/connect/logout", "/connect/register");

    private static final String RFC8414 = "/.well-known/oauth-authorization-server/t/";

    private final TenantIssuers issuers;

    public TenantIssuerFilter(TenantIssuers issuers) {
        this.issuers = issuers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith(RFC8414)) {
            // /.well-known/oauth-authorization-server/t/<slug>: the metadata of issuer <shared>/t/<slug>.
            String slug = uri.substring(RFC8414.length());
            Optional<TenantIssuers.Target> target = issuers.resolve(new PathOnly(request, "/t/" + slug + "/.well-known/oauth-authorization-server"));
            if (target.isEmpty() || !target.get().prefix().equals("/t/" + slug)) {
                notFound(response);
                return;
            }
            request.setAttribute(TenantIssuers.ATTRIBUTE, target.get());
            chain.doFilter(new PathOnly(request, "/.well-known/oauth-authorization-server"), response);
            return;
        }
        Optional<TenantIssuers.Target> target = issuers.resolve(request);
        if (target.isEmpty()) {
            if (TenantIssuers.hasTenantPrefix(uri)) {
                // /t/<unknown or suspended tenant>/...
                notFound(response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(TenantIssuers.ATTRIBUTE, target.get());
        if (target.get().prefix().isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        String rest = uri.substring(target.get().prefix().length());
        if (ENDPOINTS.stream().noneMatch(e -> e.endsWith("/") ? rest.startsWith(e) : rest.equals(e))) {
            notFound(response);
            return;
        }
        chain.doFilter(new PathOnly(request, rest), response);
    }

    private static void notFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"not_found\",\"error_description\":\"No such issuer or endpoint.\"}");
    }

    /** The request with another path (same query, host and everything else). */
    static final class PathOnly extends HttpServletRequestWrapper {

        private final String path;

        PathOnly(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + path;
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            String scheme = getScheme();
            int port = getServerPort();
            url.append(scheme).append("://").append(getServerName());
            if (!(("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443))) {
                url.append(':').append(port);
            }
            return url.append(getRequestURI());
        }
    }
}
