package com.secureone.auth.config.oauth;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.stereotype.Component;

/**
 * Runs on every {@code /oauth2/authorize} request after the built-in redirect URI and scope checks.
 * Once the user is signed in, it requires that the user belongs to the OAuth client's tenant and is
 * admitted to the client's application, whichever login method was used.
 */
@Component
public class ClientTenantAuthorizationValidator
        implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final Logger log = LoggerFactory.getLogger(ClientTenantAuthorizationValidator.class);

    private final Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> defaults =
            new OAuth2AuthorizationCodeRequestAuthenticationValidator();
    private final ApplicationOAuthClientResolver applications;
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final ApplicationAccessService access;

    public ClientTenantAuthorizationValidator(
            ApplicationOAuthClientResolver applications,
            TenantRepository tenants,
            UserAccountRepository users,
            ApplicationAccessService access) {
        this.applications = applications;
        this.tenants = tenants;
        this.users = users;
        this.access = access;
    }

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        defaults.accept(context);

        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        if (!(request.getPrincipal() instanceof Authentication principal) || !isSignedIn(principal)) {
            // Not signed in yet: the provider sends the user to the login page.
            return;
        }

        String clientId = context.getRegisteredClient().getClientId();
        Application app = applications.findActiveByOAuthClientId(clientId)
                .orElseThrow(() -> denied(request, "Client application is not active."));
        Tenant tenant = tenants.findById(app.getTenantId())
                .orElseThrow(() -> denied(request, "Client tenant not found."));

        UserAccount user = resolveTenantUser(principal.getName(), tenant)
                .orElseThrow(() -> {
                    log.warn(
                            "Denied authorize for client {}: principal is not a user of tenant {}",
                            clientId,
                            tenant.getSlug());
                    return denied(request, "This account cannot sign in to this application.");
                });

        if (!access.admit(app.getId(), user.getId())) {
            log.info("Denied authorize for client {}: user {} is not a member of the application", clientId, user.getId());
            throw denied(request, "This account has not been granted access to this application.");
        }
    }

    /** Tenant principals are named {@code tenantSlug:email}; anything else is not a user of this tenant. */
    private Optional<UserAccount> resolveTenantUser(String principalName, Tenant tenant) {
        if (principalName == null) {
            return Optional.empty();
        }
        int sep = principalName.indexOf(':');
        if (sep <= 0 || sep == principalName.length() - 1) {
            return Optional.empty();
        }
        String slug = principalName.substring(0, sep);
        if (!slug.equalsIgnoreCase(tenant.getSlug())) {
            return Optional.empty();
        }
        String email = principalName.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return users.findByTenantIdAndEmail(tenant.getId(), email);
    }

    private static boolean isSignedIn(Authentication principal) {
        return principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException denied(
            OAuth2AuthorizationCodeRequestAuthenticationToken request, String description) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, description, null), request);
    }
}
