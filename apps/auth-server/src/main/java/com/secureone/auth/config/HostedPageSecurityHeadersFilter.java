package com.secureone.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers for the hosted pages end users type passwords and codes into (sign-in, sign-up,
 * reset, security). The pages only use their own inline scripts and same-origin APIs, so the policy
 * refuses scripts from anywhere else: an injected third-party script (a compromised CDN, a browser
 * extension or proxy rewriting the page) cannot load or read the form. No {@code form-action}: browsers
 * apply it to the redirects after the sign-in form posts, which end at each app's own callback URL.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HostedPageSecurityHeadersFilter extends OncePerRequestFilter {

    static final String POLICY = String.join(
            "; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'none'");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.endsWith(".html")
                || uri.equals("/api/v1/account/email/verify")
                || uri.equals("/api/v1/account/magic-link/verify"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", POLICY);
        // Reset and verification pages carry tokens in the URL; never send it to another site. Not
        // "no-referrer": browsers then send "Origin: null" on the sign-in form post, which is refused.
        response.setHeader("Referrer-Policy", "same-origin");
        chain.doFilter(request, response);
    }
}
