package com.secureone.auth.config.oauth;

import com.secureone.auth.tenant.TenantIssuers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In the authorization server's filter chain, after Spring has set the shared issuer: a request addressed to a
 * tenant issuer (see {@link com.secureone.auth.config.TenantIssuerFilter}) is served with that issuer, so
 * discovery lists the tenant's endpoints and tokens carry it in "iss". Spring's own filter resets the context
 * when the request is done.
 */
public class TenantIssuerContextFilter extends OncePerRequestFilter {

    private record TenantContext(String issuer, AuthorizationServerSettings settings) implements AuthorizationServerContext {

        @Override
        public String getIssuer() {
            return issuer;
        }

        @Override
        public AuthorizationServerSettings getAuthorizationServerSettings() {
            return settings;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthorizationServerContext current = AuthorizationServerContextHolder.getContext();
        if (current != null && request.getAttribute(TenantIssuers.ATTRIBUTE) instanceof TenantIssuers.Target target) {
            AuthorizationServerContextHolder.setContext(
                    new TenantContext(target.issuer(), current.getAuthorizationServerSettings()));
        }
        chain.doFilter(request, response);
    }
}
