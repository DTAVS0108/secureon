package com.secureone.auth.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses HTTP Basic on admin APIs before any password is checked. Basic would bypass console MFA
 * and the sign-in rate limit; admin APIs take only console session tokens.
 */
@Component
public class AdminBasicAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (request.getRequestURI().startsWith("/api/admin/v1/")
                && header != null
                && header.regionMatches(true, 0, "Basic ", 0, 6)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"detail\":\"Admin APIs require a console session. Sign in at POST /api/admin/v1/auth/login.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
