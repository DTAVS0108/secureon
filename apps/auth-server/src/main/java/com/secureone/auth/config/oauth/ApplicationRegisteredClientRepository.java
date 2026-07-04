package com.secureone.auth.config.oauth;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSettingsService;
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

    private boolean isActive(OAuthClient client) {
        return "ACTIVE".equalsIgnoreCase(client.getStatus())
                && applications
                        .findById(client.getApplicationId())
                        .map(app -> "ACTIVE".equalsIgnoreCase(app.getStatus()))
                        .orElse(false);
    }

    private RegisteredClient toRegisteredClient(OAuthClient client) {
        Application app = applications.findById(client.getApplicationId()).orElse(null);
        String clientName = app != null ? app.getName() : client.getClientName();
        String type = client.getType() != null ? client.getType().toLowerCase(Locale.ROOT) : "web";
        boolean pkceRequired = client.isRequirePkce();
        String authMethod = client.getTokenEndpointAuthMethod() != null
                ? client.getTokenEndpointAuthMethod()
                : defaultAuthMethod(type);
        boolean publicClient = "none".equalsIgnoreCase(authMethod);

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
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            String secret = client.getClientSecret() != null ? client.getClientSecret() : "";
            if (!secret.isBlank()) {
                builder.clientSecret(encodeSecret(secret));
            }
        }

        builder.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(pkceRequired)
                .build());
        builder.tokenSettings(TokenSettingsMapper.toTokenSettings(
                applicationSettings.resolveTokenPolicy(client.getApplicationId())));

        return builder.build();
    }

    private String encodeSecret(String secret) {
        if (secret.startsWith("{bcrypt}") || secret.startsWith("$2a$") || secret.startsWith("$2b$")) {
            return secret.startsWith("{bcrypt}") ? secret : "{bcrypt}" + secret;
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
            default -> java.util.Optional.empty();
        };
    }
}
