package com.secureone.auth.config.oauth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceAuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Makes "Deny" on the device page deny. Spring's device consent provider adds the scopes the user
 * consented to in an earlier approval to the ones posted now, so posting none still approved the device
 * once the user had approved this client before. When no scope is posted, the stored consent is dropped
 * first, so the provider takes its deny path (device and user codes invalidated, device told
 * access_denied).
 */
public final class DeviceConsentDenyGuard implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationConsentService consents;

    public DeviceConsentDenyGuard(
            AuthenticationProvider delegate, RegisteredClientRepository clients, OAuth2AuthorizationConsentService consents) {
        this.delegate = delegate;
        this.clients = clients;
        this.consents = consents;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2DeviceAuthorizationConsentAuthenticationToken consent =
                (OAuth2DeviceAuthorizationConsentAuthenticationToken) authentication;
        if (consent.getScopes().isEmpty() && consent.getPrincipal() instanceof Authentication principal) {
            RegisteredClient client = clients.findByClientId(consent.getClientId());
            if (client != null) {
                OAuth2AuthorizationConsent previous = consents.findById(client.getId(), principal.getName());
                if (previous != null) {
                    consents.remove(previous);
                }
            }
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
