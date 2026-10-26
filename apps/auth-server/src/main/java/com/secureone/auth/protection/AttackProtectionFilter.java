package com.secureone.auth.protection;

import com.secureone.auth.logging.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies {@link AttackProtectionService}: blocked addresses are refused, the admin console only answers the
 * allowed addresses (when a list is set), and requests bots abuse (sign-up, password reset, magic links, one-time
 * codes, password sign-in) need a solved CAPTCHA. The token comes in the {@code X-Captcha-Token} header or, for
 * the sign-in form, the {@code captchaToken} field.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AttackProtectionFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Captcha-Token";
    public static final String TOKEN_FIELD = "captchaToken";

    private final AttackProtectionService protection;

    public AttackProtectionFilter(AttackProtectionService protection) {
        this.protection = protection;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Health checks come from the container platform itself.
        return uri.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIpResolver.resolve(request);
        String uri = request.getRequestURI();

        if (protection.isBlocked(ip)) {
            deny(response, 403, "Access from this address is blocked.");
            return;
        }
        if ((uri.equals("/admin") || uri.startsWith("/admin/") || uri.startsWith("/api/admin/"))
                && !protection.consoleAllowed(ip)) {
            deny(response, 403, "The admin console cannot be used from this address.");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && protection.enabled()) {
            if ("/login".equals(uri)) {
                if (protection.signInRequired(ip) && !protection.verify(token(request), ip)) {
                    response.sendRedirect(loginAgain(request));
                    return;
                }
            } else if (isApiSignIn(uri)) {
                // Apps with their own sign-in screens use these: the same rule as the hosted form.
                if (protection.signInRequired(ip) && !protection.verify(token(request), ip)) {
                    deny(response, 400, "Complete the CAPTCHA check and try again.");
                    return;
                }
            } else {
                String action = action(uri);
                if (action != null && protection.required(action) && !protection.verify(token(request), ip)) {
                    deny(response, 400, "Complete the CAPTCHA check and try again.");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** Password sign-in through the API (native sign-in screens). */
    static boolean isApiSignIn(String uri) {
        return "/api/v1/auth/session/login".equals(uri) || uri.matches("/api/v1/applications/[^/]+/auth/session/login");
    }

    /** Which protected action a request is, or null. */
    static String action(String uri) {
        if (uri.matches("/api/v1/applications/[^/]+/signup")) {
            return "signUp";
        }
        if (uri.contains("/account/password/forgot")) {
            return "passwordReset";
        }
        if (uri.contains("/account/magic-link/request")) {
            return "magicLink";
        }
        if (uri.contains("/auth/otp/request")) {
            return "oneTimeCodes";
        }
        return null;
    }

    private static String token(HttpServletRequest request) {
        String header = request.getHeader(TOKEN_HEADER);
        return header != null && !header.isBlank() ? header : request.getParameter(TOKEN_FIELD);
    }

    /** Back to the sign-in page with the CAPTCHA shown, keeping the application and the OAuth request. */
    private static String loginAgain(HttpServletRequest request) {
        StringBuilder url = new StringBuilder("/login.html?error=captcha");
        for (String name : new String[] {"applicationId", "continue"}) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                url.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return url.toString();
    }

    private static void deny(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"detail\":\"" + detail
                + "\",\"captchaRequired\":" + (status == 400) + "}");
    }
}
