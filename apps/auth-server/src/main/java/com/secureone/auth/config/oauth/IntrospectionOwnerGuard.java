package com.secureone.auth.config.oauth;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.tenant.RlsScope;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

/**
 * RFC 7662 introspection only for the token's own application: the client the token was issued to, or another
 * client of the same application (its backend, its M2M client). Any other client, including one of another
 * tenant, is told {@code active: false}, as if the token did not exist (RFC 7662 section 2.2), so introspection
 * reveals nothing about other applications' users. Spring's default answers any authenticated client.
 */
public final class IntrospectionOwnerGuard implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(IntrospectionOwnerGuard.class);

    private final AuthenticationProvider delegate;
    private final ApplicationOAuthClientResolver applications;

    public IntrospectionOwnerGuard(AuthenticationProvider delegate, ApplicationOAuthClientResolver applications) {
        this.delegate = delegate;
        this.applications = applications;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication result = delegate.authenticate(authentication);
        if (!(result instanceof OAuth2TokenIntrospectionAuthenticationToken introspection)
                || !introspection.getTokenClaims().isActive()
                || !(introspection.getPrincipal() instanceof OAuth2ClientAuthenticationToken caller)
                || caller.getRegisteredClient() == null) {
            return result;
        }
        String requester = caller.getRegisteredClient().getClientId();
        String issuedTo = introspection.getTokenClaims().getClientId();
        if (mayIntrospect(requester, issuedTo)) {
            return result;
        }
        log.info("Introspection by client {} of a token issued to another application's client: answered inactive", requester);
        return new OAuth2TokenIntrospectionAuthenticationToken(
                introspection.getToken(), caller, OAuth2TokenIntrospection.builder().build());
    }

    private boolean mayIntrospect(String requester, @Nullable String issuedTo) {
        if (issuedTo == null) {
            return false;
        }
        if (requester.equals(issuedTo)) {
            return true;
        }
        UUID requesterApp = applicationOf(requester);
        return requesterApp != null && Objects.equals(requesterApp, applicationOf(issuedTo));
    }

    private @Nullable UUID applicationOf(String clientId) {
        Optional<Application> app = RlsScope.asPlatform(() -> applications.findActiveByOAuthClientId(clientId));
        return app.map(Application::getId).orElse(null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
