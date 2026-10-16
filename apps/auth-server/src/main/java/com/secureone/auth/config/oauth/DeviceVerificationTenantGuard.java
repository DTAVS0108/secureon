package com.secureone.auth.config.oauth;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceVerificationAuthenticationToken;

/**
 * Wraps Spring's device verification provider so approving a device (RFC 8628) follows the same rule as
 * /oauth2/authorize: the signed-in user must belong to the client's tenant and be admitted to its
 * application. Without it, any signed-in user of any tenant could approve any device code.
 */
public final class DeviceVerificationTenantGuard implements AuthenticationProvider {

    /** Error description that marks a refusal by the tenant/app rule (vs. the user denying). */
    public static final String NOT_ALLOWED = "This account cannot connect devices to this application.";

    private final AuthenticationProvider delegate;
    private final DeviceCodeLookup devices;
    private final ClientTenantAuthorizationValidator tenantRule;

    public DeviceVerificationTenantGuard(
            AuthenticationProvider delegate, DeviceCodeLookup devices, ClientTenantAuthorizationValidator tenantRule) {
        this.delegate = delegate;
        this.devices = devices;
        this.tenantRule = tenantRule;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2DeviceVerificationAuthenticationToken request = (OAuth2DeviceVerificationAuthenticationToken) authentication;
        if (request.getPrincipal() instanceof Authentication principal
                && principal.isAuthenticated()
                && !(principal instanceof AnonymousAuthenticationToken)) {
            DeviceCodeLookup.PendingDevice device = devices.pending(request.getUserCode())
                    .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(
                            OAuth2ErrorCodes.INVALID_GRANT, "This code is invalid or has expired.", null)));
            Optional<String> denial = tenantRule.denial(device.clientId(), principal);
            if (denial.isPresent()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, NOT_ALLOWED, null), denial.get());
            }
        }
        // Not signed in yet: the provider returns the request unauthenticated and the user is sent to log in.
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
