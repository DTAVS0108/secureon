package com.secureone.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OAuthTokenExchangeStubFilter extends OncePerRequestFilter {

    private static final String TOKEN_EXCHANGE_GRANT =
            "urn:ietf:params:oauth:grant-type:token-exchange";

    private final ObjectMapper objectMapper;

    public OAuthTokenExchangeStubFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isTokenExchangeRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "unsupported_grant_type");
        body.put(
                "error_description",
                "OAuth 2.0 Token Exchange (RFC 8693) is not yet implemented in SecureOne.");
        response.setStatus(HttpStatus.NOT_IMPLEMENTED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }

    private static boolean isTokenExchangeRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if (!"/oauth2/token".equals(request.getRequestURI())) {
            return false;
        }
        String grantType = request.getParameter("grant_type");
        return TOKEN_EXCHANGE_GRANT.equals(grantType);
    }
}
