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
 * Lets a public client (a TV app, CLI or other device that cannot keep a secret) use the device
 * authorization grant (RFC 8628) with client_id alone, on the device authorization request and when
 * polling the token endpoint. Spring Authorization Server authenticates public clients only by PKCE on
 * the code exchange. The user still approves the device in a browser, and the device code is single use.
 */
public final class PublicClientDeviceAuthentication {

    public static final String DEVICE_AUTHORIZATION_PATH = "/oauth2/device_authorization";
    private static final String MARKER = "secureone_public_device";

    private PublicClientDeviceAuthentication() {}

    public static final class Converter implements AuthenticationConverter {
        @Override
        public @Nullable Authentication convert(HttpServletRequest request) {
            boolean deviceRequest = DEVICE_AUTHORIZATION_PATH.equals(request.getRequestURI());
            boolean deviceGrant = AuthorizationGrantType.DEVICE_CODE.getValue()
                    .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
            if (!deviceRequest && !deviceGrant) {
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
            additional.put(MARKER, Boolean.TRUE);
            return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null, additional);
        }
    }

    /** Accepts it only for clients registered as public with the device_code grant. */
    public static final class Provider implements AuthenticationProvider {
        private final RegisteredClientRepository clients;

        public Provider(RegisteredClientRepository clients) {
            this.clients = clients;
        }

        @Override
        public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
            OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) authentication;
            if (!ClientAuthenticationMethod.NONE.equals(token.getClientAuthenticationMethod())
                    || !Boolean.TRUE.equals(token.getAdditionalParameters().get(MARKER))) {
                return null;
            }
            RegisteredClient client = clients.findByClientId(String.valueOf(token.getPrincipal()));
            if (client == null
                    || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                    || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.DEVICE_CODE)) {
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
