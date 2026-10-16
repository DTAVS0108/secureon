package com.secureone.auth.ratelimit;

import com.secureone.auth.logging.ClientIpResolver;
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
        // Device sign-in: issuing codes, and checking a typed code (guessing must stay slow).
        if ("/oauth2/device_authorization".equals(uri)) {
            return new RateLimitRule("device-authorization", properties.getTokenPerMinute());
        }
        if (uri.startsWith("/api/v1/auth/device/")) {
            return new RateLimitRule("device-lookup", properties.getOtpRequestPerMinute());
        }
        if (uri.contains("/auth/mfa/")) {
            return new RateLimitRule("mfa-verify", properties.getOtpRequestPerMinute());
        }
        if (uri.contains("/auth/otp/request") || (uri.contains("/account/verify/") && uri.endsWith("/request"))) {
            return new RateLimitRule("otp-request", properties.getOtpRequestPerMinute());
        }
        // Each of these sends an email or creates an account for whatever address it is given.
        if (uri.matches("/api/v1/applications/[^/]+/signup")) {
            return new RateLimitRule("signup", properties.getSignupPerMinute());
        }
        if (uri.contains("/account/magic-link/request") || uri.contains("/account/email/resend-verification")) {
            return new RateLimitRule("email-request", properties.getEmailRequestPerMinute());
        }
        if ("/api/admin/v1/auth/login".equals(uri)) {
            return new RateLimitRule("admin-login", properties.getAdminLoginPerMinute());
        }
        return null;
    }

    private static String clientKey(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        return ip != null ? ip : "unknown";
    }

    private record RateLimitRule(String name, int limit) {}
}
