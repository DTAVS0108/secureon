package com.secureone.auth.cache;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * After a successful change through an API that edits configuration (admin API, SCIM, first-run setup,
 * client registration), clears the {@link ConfigCache} on every instance, so the change applies to the next
 * request. Outermost filter: it sees the final status. Failed requests change nothing and clear nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfigChangeFilter extends OncePerRequestFilter {

    static final List<String> CONFIG_PATHS = List.of(
            "/api/admin/", "/scim/v2", "/api/v1/setup/", "/oauth2/register", "/connect/register");
    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ConfigCache cache;

    public ConfigChangeFilter(ConfigCache cache) {
        this.cache = cache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (READS.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return CONFIG_PATHS.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (response.getStatus() < 400) {
                cache.changed();
            }
        }
    }
}
