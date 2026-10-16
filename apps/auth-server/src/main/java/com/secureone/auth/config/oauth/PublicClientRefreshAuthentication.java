package com.secureone.auth.config.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Lets a public (PKCE, no secret) client use the refresh_token grant. Spring Authorization Server
 * authenticates public clients only on the code exchange (by the PKCE verifier), so the refresh tokens
 * {@link PublicClientRefreshTokenGenerator} issues to SPAs could never be used. A public client is
 * identified by client_id alone on this grant; refresh-token rotation and reuse detection
 * ({@link ReuseDetectingOAuth2AuthorizationService}) limit what a stolen token is worth, as for any SPA.
 */
public final class PublicClientRefreshAuthentication {

    private static final String GRANT_MARKER = "secureone_public_refresh";

    private PublicClientRefreshAuthentication() {}

    /** Turns {@code grant_type=refresh_token&client_id=…} (no secret, no Authorization header) into a client authentication. */
    public static final class Converter implements AuthenticationConverter {
        @Override
        public @Nullable Authentication convert(HttpServletRequest request) {
            if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
                return null;
            }
            if (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                    || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null
                    || request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null) {
                return null;
            }
            String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
            if (clientIds == null || clientIds.length != 1 || clientIds[0].isBlank()) {
                return null;
            }
            Map<String, Object> additional = new HashMap<>();
            additional.put(GRANT_MARKER, Boolean.TRUE);
            return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null, additional);
        }
    }

    /**
     * Accepts it only for clients registered as public with the refresh_token grant that either require
     * PKCE (SPAs, mobile) or use the device grant (TVs, CLIs, which cannot do PKCE redirects).
     */
    public static final class Provider implements AuthenticationProvider {
        private final RegisteredClientRepository clients;

        public Provider(RegisteredClientRepository clients) {
            this.clients = clients;
        }

        @Override
        public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
            OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) authentication;
            if (!ClientAuthenticationMethod.NONE.equals(token.getClientAuthenticationMethod())
                    || !Boolean.TRUE.equals(token.getAdditionalParameters().get(GRANT_MARKER))) {
                return null;
            }
            RegisteredClient client = clients.findByClientId(String.valueOf(token.getPrincipal()));
            if (client == null
                    || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                    || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)
                    || !(client.getClientSettings().isRequireProofKey()
                            || client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.DEVICE_CODE))) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
            }
            return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
