package com.secureone.auth.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("secureoneRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(RequestContext.REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);

        String sessionId = resolveSessionId(request);
        if (sessionId != null) {
            MDC.put(RequestContext.SESSION_ID, sessionId);
        }

        String clientIp = ClientIpResolver.resolve(request);
        if (clientIp != null) {
            MDC.put(RequestContext.CLIENT_IP, clientIp);
        }

        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && !userAgent.isBlank()) {
            MDC.put(RequestContext.USER_AGENT, userAgent.trim());
        }

        String applicationHeader = request.getHeader("X-Application-Id");
        if (applicationHeader != null && !applicationHeader.isBlank()) {
            MDC.put(RequestContext.APPLICATION_ID, applicationHeader.trim());
        }

        long started = System.currentTimeMillis();
        try {
            log.debug("{} {}{}", request.getMethod(), request.getRequestURI(), querySuffix(request));
            filterChain.doFilter(request, response);
            populatePrincipalMdc();
            long elapsed = System.currentTimeMillis() - started;
            log.info(
                    "{} {} -> {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed);
        } finally {
            MDC.clear();
        }
    }

    private void populatePrincipalMdc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }
        String name = auth.getName();
        MDC.put(RequestContext.PRINCIPAL, name);
        int sep = name.indexOf(':');
        if (sep > 0) {
            MDC.put(RequestContext.TENANT_SLUG, name.substring(0, sep).trim().toLowerCase(Locale.ROOT));
        }
    }

    private static String resolveSessionId(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JSESSIONID".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : null;
    }

    private static String querySuffix(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? "" : "?" + query;
    }
}
