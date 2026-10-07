package com.secureone.auth.oauth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.secureone.auth.config.oauth.ApplicationRegisteredClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthTokenExchangeService {

    static final AuthorizationGrantType TOKEN_EXCHANGE_GRANT =
            new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange");

    private static final String SUBJECT_ACCESS_TOKEN =
            "urn:ietf:params:oauth:token-type:access_token";

    private static final String ISSUED_ACCESS_TOKEN =
            "urn:ietf:params:oauth:token-type:access_token";

    private final ApplicationRegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final JwtDecoder jwtDecoder;
    private final NimbusJwtEncoder jwtEncoder;

    public OAuthTokenExchangeService(
            ApplicationRegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            JwtDecoder jwtDecoder,
            JWKSource<SecurityContext> jwkSource) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }

    public Map<String, Object> exchange(HttpServletRequest request) {
        RegisteredClient client = authenticateClient(request);
        String subjectToken = requiredParam(request, OAuth2ParameterNames.SUBJECT_TOKEN);
        String subjectTokenType = param(request, OAuth2ParameterNames.SUBJECT_TOKEN_TYPE);
        if (subjectTokenType != null
                && !SUBJECT_ACCESS_TOKEN.equals(subjectTokenType)
                && !"access_token".equalsIgnoreCase(subjectTokenType)) {
            throw invalidGrant("Unsupported subject_token_type.");
        }

        Jwt subjectJwt = decodeSubjectToken(subjectToken);
        verifySubjectAudience(subjectJwt, client);

        Set<String> subjectScopes = scopesOf(subjectJwt.getClaim("scope"));
        Set<String> requestedScopes = scopesFromClaim(param(request, OAuth2ParameterNames.SCOPE));
        Set<String> grantedScopes = resolveGrantedScopes(subjectScopes, requestedScopes);

        OAuth2AccessToken accessToken = issueAccessToken(client, subjectJwt, grantedScopes);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(OAuth2ParameterNames.ACCESS_TOKEN, accessToken.getTokenValue());
        out.put(OAuth2ParameterNames.TOKEN_TYPE, OAuth2AccessToken.TokenType.BEARER.getValue());
        out.put(OAuth2ParameterNames.EXPIRES_IN, expiresInSeconds(accessToken));
        out.put("issued_token_type", ISSUED_ACCESS_TOKEN);
        if (!grantedScopes.isEmpty()) {
            out.put(OAuth2ParameterNames.SCOPE, String.join(" ", grantedScopes));
        }
        return out;
    }

    private RegisteredClient authenticateClient(HttpServletRequest request) {
        String clientId = param(request, OAuth2ParameterNames.CLIENT_ID);
        String clientSecret = param(request, OAuth2ParameterNames.CLIENT_SECRET);
        String basic = request.getHeader("Authorization");
        if (basic != null && basic.regionMatches(true, 0, "Basic ", 0, 6)) {
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(basic.substring(6).trim()), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                throw invalidClient("Malformed Basic authorization header.");
            }
            int sep = decoded.indexOf(':');
            if (sep <= 0) {
                throw invalidClient("Malformed Basic authorization header.");
            }
            // RFC 6749 2.3.1: client id and secret are form-urlencoded inside the Basic credentials.
            clientId = URLDecoder.decode(decoded.substring(0, sep), StandardCharsets.UTF_8);
            clientSecret = URLDecoder.decode(decoded.substring(sep + 1), StandardCharsets.UTF_8);
        }
        if (!StringUtils.hasText(clientId)) {
            throw invalidClient("client_id is required.");
        }
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) {
            throw invalidClient("Unknown client_id.");
        }
        // Exchanging another party's token requires an authenticated (confidential) client.
        if (client.getClientSecret() == null
                || client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw invalidClient("Token exchange requires a confidential client.");
        }
        if (!StringUtils.hasText(clientSecret)) {
            throw invalidClient("client_secret is required.");
        }
        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw invalidClient("Invalid client credentials.");
        }
        if (!client.getAuthorizationGrantTypes().contains(TOKEN_EXCHANGE_GRANT)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unauthorized_client", "Client is not allowed to use the token-exchange grant.", null));
        }
        return client;
    }

    /** The JWT scope claim may be a space-delimited string or a JSON array. */
    private static Set<String> scopesOf(Object claim) {
        if (claim instanceof java.util.Collection<?> values) {
            Set<String> scopes = new LinkedHashSet<>();
            for (Object value : values) {
                if (value != null && !value.toString().isBlank()) {
                    scopes.add(value.toString().trim());
                }
            }
            return scopes;
        }
        return scopesFromClaim(claim != null ? claim.toString() : null);
    }

    private Jwt decodeSubjectToken(String subjectToken) {
        try {
            return jwtDecoder.decode(subjectToken);
        } catch (JwtException ex) {
            throw invalidGrant("Invalid or expired subject_token.");
        }
    }

    private void verifySubjectAudience(Jwt subjectJwt, RegisteredClient client) {
        Object aud = subjectJwt.getAudience();
        if (aud != null && !aud.toString().isBlank()) {
            List<String> audienceList = subjectJwt.getAudience() != null ? subjectJwt.getAudience() : List.of();
            boolean matches = audienceList.stream().anyMatch(a -> client.getClientId().equals(a));
            if (!matches) {
                throw invalidGrant("subject_token audience does not match requesting client.");
            }
        }
        String azp = subjectJwt.getClaimAsString("azp");
        if (azp != null && !azp.isBlank() && !client.getClientId().equals(azp)) {
            throw invalidGrant("subject_token authorized party does not match requesting client.");
        }
    }

    private static Set<String> resolveGrantedScopes(Set<String> subjectScopes, Set<String> requestedScopes) {
        if (requestedScopes.isEmpty()) {
            return subjectScopes;
        }
        Set<String> granted = new LinkedHashSet<>();
        for (String scope : requestedScopes) {
            if (subjectScopes.contains(scope)) {
                granted.add(scope);
            }
        }
        if (granted.isEmpty()) {
            throw invalidGrant("Requested scope is not allowed by subject_token.");
        }
        return granted;
    }

    private OAuth2AccessToken issueAccessToken(RegisteredClient client, Jwt subjectJwt, Set<String> grantedScopes) {
        TokenSettings tokenSettings = client.getTokenSettings();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenSettings.getAccessTokenTimeToLive());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(subjectJwt.getSubject())
                .issuer(subjectJwt.getIssuer().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("azp", client.getClientId())
                .audience(List.of(client.getClientId()));
        if (!grantedScopes.isEmpty()) {
            claims.claim("scope", String.join(" ", grantedScopes));
        }

        String tokenValue = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims.build()))
                .getTokenValue();
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                issuedAt,
                expiresAt,
                grantedScopes);
    }

    private static long expiresInSeconds(OAuth2AccessToken accessToken) {
        if (accessToken.getExpiresAt() == null) {
            return 0L;
        }
        long seconds = accessToken.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0L, seconds);
    }

    private static Set<String> scopesFromClaim(String scopeClaim) {
        Set<String> scopes = new LinkedHashSet<>();
        if (!StringUtils.hasText(scopeClaim)) {
            return scopes;
        }
        for (String part : scopeClaim.split("\\s+")) {
            if (!part.isBlank()) {
                scopes.add(part.trim());
            }
        }
        return scopes;
    }

    private static String requiredParam(HttpServletRequest request, String name) {
        String value = param(request, name);
        if (!StringUtils.hasText(value)) {
            throw invalidGrant(name + " is required.");
        }
        return value;
    }

    private static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value != null ? value.trim() : null;
    }

    private static OAuth2AuthenticationException invalidGrant(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_grant", description, null));
    }

    private static OAuth2AuthenticationException invalidClient(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_client", description, null));
    }

    Map<String, Object> errorBody(OAuth2AuthenticationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        OAuth2Error error = ex.getError();
        body.put("error", error.getErrorCode());
        if (StringUtils.hasText(error.getDescription())) {
            body.put("error_description", error.getDescription());
        }
        return body;
    }

    int statusFor(OAuth2AuthenticationException ex) {
        return "invalid_client".equals(ex.getError().getErrorCode())
                ? HttpStatus.UNAUTHORIZED.value()
                : HttpStatus.BAD_REQUEST.value();
    }
}
