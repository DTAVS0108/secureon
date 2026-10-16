package com.secureone.auth.config.oauth;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationDisplayName;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

/** Finds the pending device authorization (RFC 8628) a user code belongs to, and its application. */
@Component
public class DeviceCodeLookup {

    private static final OAuth2TokenType USER_CODE = new OAuth2TokenType(OAuth2ParameterNames.USER_CODE);

    private final OAuth2AuthorizationService authorizations;
    private final RegisteredClientRepository clients;
    private final ApplicationOAuthClientResolver applications;
    private final ApplicationDisplayName displayNames;

    public DeviceCodeLookup(
            OAuth2AuthorizationService authorizations,
            RegisteredClientRepository clients,
            ApplicationOAuthClientResolver applications,
            ApplicationDisplayName displayNames) {
        this.authorizations = authorizations;
        this.clients = clients;
        this.applications = applications;
        this.displayNames = displayNames;
    }

    public record PendingDevice(String clientId, UUID applicationId, String applicationName, List<String> scopes) {}

    /** A user code that is still waiting for approval (not expired, not used, not denied). */
    public Optional<PendingDevice> pending(String userCode) {
        String normalized = normalize(userCode);
        if (normalized == null) {
            return Optional.empty();
        }
        OAuth2Authorization authorization = authorizations.findByToken(normalized, USER_CODE);
        if (authorization == null) {
            return Optional.empty();
        }
        OAuth2Authorization.Token<OAuth2UserCode> code = authorization.getToken(OAuth2UserCode.class);
        OAuth2Authorization.Token<OAuth2DeviceCode> device = authorization.getToken(OAuth2DeviceCode.class);
        if (code == null || !code.isActive() || device == null || !device.isActive()
                || code.getToken().getExpiresAt() == null || code.getToken().getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        if (client == null) {
            return Optional.empty();
        }
        Optional<Application> app = applications.findActiveByOAuthClientId(client.getClientId());
        if (app.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PendingDevice(
                client.getClientId(),
                app.get().getId(),
                displayNames.of(app.get()),
                requestedScopes(authorization)));
    }

    // Scopes are stored as an attribute until the user approves the device.
    private static List<String> requestedScopes(OAuth2Authorization authorization) {
        Object requested = authorization.getAttribute(OAuth2ParameterNames.SCOPE);
        if (requested instanceof java.util.Collection<?> scopes && !scopes.isEmpty()) {
            return scopes.stream().map(String::valueOf).sorted().toList();
        }
        return authorization.getAuthorizedScopes().stream().sorted().toList();
    }

    /** Users type codes in any case, with or without the dash ("wdjb-mjht", "WDJBMJHT"). */
    public static String normalize(String userCode) {
        if (userCode == null) {
            return null;
        }
        String letters = userCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
        if (letters.length() != 8) {
            return null;
        }
        return letters.substring(0, 4) + "-" + letters.substring(4);
    }
}
