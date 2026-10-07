package com.secureone.auth.serviceaccount;

import com.secureone.auth.ratelimit.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** Per-key requests per minute for /api/v1/m2m/** */
    static final int M2M_RATE_LIMIT_PER_MINUTE = 120;

    private final ServiceAccountService serviceAccounts;
    private final RedisRateLimiter rateLimiter;

    public ApiKeyAuthenticationFilter(ServiceAccountService serviceAccounts, RedisRateLimiter rateLimiter) {
        this.serviceAccounts = serviceAccounts;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/m2m/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null
                && existing.isAuthenticated()
                && !(existing instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = extractApiKey(request);
        if (rawKey != null && rawKey.startsWith("sk_live_")) {
            var context = serviceAccounts.validateApiKey(rawKey);
            if (context.isPresent()) {
                String bucket = "m2m:key:" + context.get().apiKeyId();
                if (!rateLimiter.tryConsume(bucket, M2M_RATE_LIMIT_PER_MINUTE)) {
                    response.sendError(429, "API key rate limit exceeded.");
                    return;
                }
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        context.get(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_M2M")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        String apiKey = request.getHeader("X-Api-Key");
        return apiKey != null ? apiKey.trim() : null;
    }
}
