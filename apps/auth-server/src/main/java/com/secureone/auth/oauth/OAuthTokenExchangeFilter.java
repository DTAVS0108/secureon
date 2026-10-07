package com.secureone.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OAuthTokenExchangeFilter extends OncePerRequestFilter {

    private final OAuthTokenExchangeService tokenExchange;
    private final ObjectMapper objectMapper;

    public OAuthTokenExchangeFilter(OAuthTokenExchangeService tokenExchange, ObjectMapper objectMapper) {
        this.tokenExchange = tokenExchange;
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
        try {
            Map<String, Object> body = tokenExchange.exchange(request);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), body);
        } catch (OAuth2AuthenticationException ex) {
            response.setStatus(tokenExchange.statusFor(ex));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), tokenExchange.errorBody(ex));
        }
    }

    private static boolean isTokenExchangeRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/oauth2/token".equals(request.getRequestURI())
                && OAuthTokenExchangeService.TOKEN_EXCHANGE_GRANT
                        .getValue()
                        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
    }
}
