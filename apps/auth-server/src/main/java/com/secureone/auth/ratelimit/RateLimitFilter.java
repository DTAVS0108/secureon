package com.secureone.auth.ratelimit;

import com.secureone.auth.config.SecureOneRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecureOneRateLimitProperties properties;
    private final RedisRateLimiter rateLimiter;

    public RateLimitFilter(SecureOneRateLimitProperties properties, RedisRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = resolveRule(request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = clientKey(request);
        String bucket = rule.name() + ":" + clientKey;
        if (rateLimiter.tryConsume(bucket, rule.limit())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again in a minute.");
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\""
                        + problem.getDetail()
                        + "\"}");
    }

    private RateLimitRule resolveRule(String uri) {
        if ("/login".equals(uri) || uri.endsWith("/auth/session/login")) {
            return new RateLimitRule("login", properties.getLoginPerMinute());
        }
        if ("/oauth2/token".equals(uri)) {
            return new RateLimitRule("token", properties.getTokenPerMinute());
        }
        if (uri.contains("/account/password/forgot")) {
            return new RateLimitRule("forgot-password", properties.getForgotPasswordPerMinute());
        }
        if (uri.contains("/auth/mfa/")) {
            return new RateLimitRule("mfa-verify", properties.getOtpRequestPerMinute());
        }
        if (uri.contains("/auth/otp/request") || (uri.contains("/account/verify/") && uri.endsWith("/request"))) {
            return new RateLimitRule("otp-request", properties.getOtpRequestPerMinute());
        }
        if ("/api/admin/v1/auth/login".equals(uri)) {
            return new RateLimitRule("admin-login", properties.getAdminLoginPerMinute());
        }
        return null;
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private record RateLimitRule(String name, int limit) {}
}
