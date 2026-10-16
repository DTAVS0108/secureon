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
        Optional<String> denial = denial(clientId, principal);
        if (denial.isPresent()) {
            throw denied(request, denial.get());
        }
    }

    /**
     * Why a signed-in principal may not authorize this client, if it may not: the user must belong to
     * the client's tenant and be admitted to its application. Shared by the authorize and device flows.
     */
    public Optional<String> denial(String clientId, Authentication principal) {
        Optional<Application> app = applications.findActiveByOAuthClientId(clientId);
        if (app.isEmpty()) {
            return Optional.of("Client application is not active.");
        }
        Optional<Tenant> tenant = tenants.findById(app.get().getTenantId());
        if (tenant.isEmpty()) {
            return Optional.of("Client tenant not found.");
        }
        Optional<UserAccount> user = resolveTenantUser(principal.getName(), tenant.get());
        if (user.isEmpty()) {
            log.warn(
                    "Denied authorization for client {}: principal is not a user of tenant {}",
                    clientId,
                    tenant.get().getSlug());
            return Optional.of("This account cannot sign in to this application.");
        }
        if (!access.admit(app.get().getId(), user.get().getId())) {
            log.info("Denied authorization for client {}: user {} is not a member of the application", clientId, user.get().getId());
            return Optional.of("This account has not been granted access to this application.");
        }
        return Optional.empty();
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
