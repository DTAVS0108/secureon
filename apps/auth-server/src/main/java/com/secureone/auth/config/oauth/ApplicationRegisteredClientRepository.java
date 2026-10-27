package com.secureone.auth.config.oauth;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.oauth.ClientSecrets;
import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads OAuth clients from oauth_client records (admin-registered credentials). */
@Service
@Transactional(readOnly = true)
public class ApplicationRegisteredClientRepository implements RegisteredClientRepository {

    private final OAuthClientRepository oauthClients;
    private final ApplicationRepository applications;
    private final ApplicationSettingsService applicationSettings;
    private final PasswordEncoder passwordEncoder;

    public ApplicationRegisteredClientRepository(
            OAuthClientRepository oauthClients,
            ApplicationRepository applications,
            ApplicationSettingsService applicationSettings,
            PasswordEncoder passwordEncoder) {
        this.oauthClients = oauthClients;
        this.applications = applications;
        this.applicationSettings = applicationSettings;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "OAuth clients are managed via the admin OAuth Clients API.");
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            return oauthClients
                    .findById(UUID.fromString(id))
                    .filter(this::isActive)
                    .map(this::toRegisteredClient)
                    .orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return oauthClients
                .findByClientId(clientId.trim())
                .filter(this::isActive)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    /** The client, its application and its organization must all be active (a suspended tenant gets no tokens). */
    private boolean isActive(OAuthClient client) {
        return "ACTIVE".equalsIgnoreCase(client.getStatus())
                && applications
                        .findById(client.getApplicationId())
                        .filter(app -> "ACTIVE".equalsIgnoreCase(app.getStatus()))
                        .map(app -> tenants == null || tenants.findById(app.getTenantId())
                                .map(t -> "ACTIVE".equalsIgnoreCase(t.getStatus()))
                                .orElse(false))
                        .orElse(false);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.secureone.auth.tenant.TenantRepository tenants;

    private RegisteredClient toRegisteredClient(OAuthClient client) {
        Application app = applications.findById(client.getApplicationId()).orElse(null);
        String clientName = app != null ? app.getName() : client.getClientName();
        String type = client.getType() != null ? client.getType().toLowerCase(Locale.ROOT) : "web";
        boolean pkceRequired = client.isRequirePkce();
        String authMethod = client.getTokenEndpointAuthMethod() != null
                ? client.getTokenEndpointAuthMethod()
                : defaultAuthMethod(type);
        boolean publicClient = "none".equalsIgnoreCase(authMethod);
        boolean privateKeyJwt = OAuthClient.AUTH_PRIVATE_KEY_JWT.equalsIgnoreCase(authMethod);

        RegisteredClient.Builder builder = RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientName(clientName);

        List<String> grantTypes = client.getGrantTypes();
        for (String grant : grantTypes) {
            mapGrantType(grant).ifPresent(builder::authorizationGrantType);
        }
        if (grantTypes.isEmpty()) {
            builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }

        for (String uri : client.getRedirectUris()) {
            if (uri != null && !uri.isBlank()) {
                builder.redirectUri(uri.trim());
            }
        }
        for (String uri : client.getPostLogoutRedirectUris()) {
            if (uri != null && !uri.isBlank()) {
                builder.postLogoutRedirectUri(uri.trim());
            }
        }

        List<String> scopes = client.getScopes();
        if (scopes.isEmpty()) {
            builder.scope("openid").scope("profile").scope("email");
        } else {
            scopes.forEach(builder::scope);
        }

        if (publicClient) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else if (privateKeyJwt) {
            // Only a signed assertion: a secret the client may still have stored is not accepted.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            String secret = client.getClientSecret() != null ? client.getClientSecret() : "";
            if (!secret.isBlank()) {
                String previous = client.getPreviousClientSecret();
                if (previous != null && client.getPreviousSecretExpiresAt() != null
                        && client.getPreviousSecretExpiresAt().isAfter(java.time.Instant.now())) {
                    // Rotation overlap: the old secret works too until it expires.
                    builder.clientSecret(com.secureone.auth.config.AppConfig.DualSecret.encoded(
                            encodeSecret(secret), encodeSecret(previous), client.getPreviousSecretExpiresAt()));
                } else {
                    builder.clientSecret(encodeSecret(secret));
                }
            }
        }

        ClientSettings.Builder clientSettings = ClientSettings.builder()
                // Third-party clients ask the user first (consent screen at /oauth2/consent).
                .requireAuthorizationConsent(client.isConsentRequired())
                .requireProofKey(pkceRequired)
                .setting(SubjectClaims.SETTING, client.getSubjectType() != null ? client.getSubjectType() : OAuthClient.SUBJECT_LEGACY);
        if (privateKeyJwt) {
            // Read by ClientAssertionDecoderFactory: the stored JWKS, else the client's JWKS URL.
            if (client.getJwks() != null && !client.getJwks().isBlank()) {
                clientSettings.setting(ClientAssertionDecoderFactory.JWKS_SETTING, client.getJwks());
            } else if (client.getJwksUri() != null && !client.getJwksUri().isBlank()) {
                clientSettings.jwkSetUrl(client.getJwksUri());
            }
        }
        builder.clientSettings(clientSettings.build());
        TokenSettings tokenSettings = TokenSettingsMapper.toTokenSettings(
                applicationSettings.resolveTokenPolicy(client.getApplicationId()));
        builder.tokenSettings(tokenSettings);
        if (!TokenSettingsMapper.refreshTokensEnabled(tokenSettings)) {
            // Token policy turned refresh tokens off: no refresh token is issued for any grant.
            builder.authorizationGrantTypes(types -> types.remove(AuthorizationGrantType.REFRESH_TOKEN));
        }

        return builder.build();
    }

    /**
     * The client authentication checks secrets with the BCrypt {@link PasswordEncoder} bean, which reads a
     * bare hash: stored "{bcrypt}$2a$..." loses its prefix here. A legacy plain secret (not yet hashed by
     * the start-up upgrade) is hashed on the fly.
     */
    private String encodeSecret(String secret) {
        if (ClientSecrets.isHashed(secret)) {
            return secret.startsWith(ClientSecrets.PREFIX) ? secret.substring(ClientSecrets.PREFIX.length()) : secret;
        }
        return passwordEncoder.encode(secret);
    }

    private static String defaultAuthMethod(String type) {
        return "m2m".equals(type) || "web".equals(type) ? "client_secret_basic" : "none";
    }

    private static java.util.Optional<AuthorizationGrantType> mapGrantType(String grant) {
        if (grant == null) {
            return java.util.Optional.empty();
        }
        return switch (grant.trim().toLowerCase(Locale.ROOT)) {
            case "authorization_code" -> java.util.Optional.of(AuthorizationGrantType.AUTHORIZATION_CODE);
            case "refresh_token" -> java.util.Optional.of(AuthorizationGrantType.REFRESH_TOKEN);
            case "client_credentials" -> java.util.Optional.of(AuthorizationGrantType.CLIENT_CREDENTIALS);
            case "urn:ietf:params:oauth:grant-type:device_code", "device_code" ->
                    java.util.Optional.of(AuthorizationGrantType.DEVICE_CODE);
            case "urn:ietf:params:oauth:grant-type:token-exchange", "token_exchange", "token-exchange" ->
                    java.util.Optional.of(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"));
            default -> java.util.Optional.empty();
        };
    }
}
