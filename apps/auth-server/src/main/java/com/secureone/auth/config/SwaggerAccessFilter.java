package com.secureone.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Blocks Swagger/OpenAPI endpoints when disabled for the active environment. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SwaggerAccessFilter extends OncePerRequestFilter {

    private final SecureOneEnvironmentProperties environment;

    public SwaggerAccessFilter(SecureOneEnvironmentProperties environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (environment.isSwaggerEnabled() || !isDocsPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.NOT_FOUND.value());
    }

    private static boolean isDocsPath(String uri) {
        return uri.startsWith("/docs")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs");
    }
}
