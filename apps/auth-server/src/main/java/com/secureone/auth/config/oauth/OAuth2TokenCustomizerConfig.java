package com.secureone.auth.config.oauth;

import com.secureone.auth.application.ApplicationAccessClaims;
import com.secureone.auth.authn.AuthContextAttributes;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.user.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class OAuth2TokenCustomizerConfig {

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtEncodingContextCustomizer(
            ApplicationAccessClaims accessClaims,
            TenantUserDetailsService users,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.OAuthClientRepository> clients,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSettingsService> settings,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.tenant.TenantRepository> tenants) {
        return context -> {
            requireActiveUser(context, users);
            addProfileClaims(context, users);
            SubjectClaims.apply(context, users);
            // An administrator signed in as this user: the app sees who is really acting (RFC 8693 "act").
            String impersonator = AuthContextAttributes.impersonatedBy(context.getPrincipal());
            if (impersonator != null) {
                context.getClaims().claims(map -> map.putIfAbsent("act", java.util.Map.of("sub", impersonator)));
            }
            List<String> amr = AuthContextAttributes.amrFromCurrentRequest();
            String acr = AuthContextAttributes.acrFromCurrentRequest();
            if (!amr.isEmpty()) {
                context.getClaims().claim("amr", amr);
            }
            if (acr != null && !acr.isBlank() && !"0".equals(acr)) {
                context.getClaims().claim("acr", acr);
            }
            applyPolicyLifetime(context);
            addApplicationAccess(context, accessClaims);
            addApplicationClaims(context, users, clients, settings, tenants);
        };
    }

    /** The application's own claims (Integration › Token claims), added last so they never replace ours. */
    private static void addApplicationClaims(
            JwtEncodingContext context,
            TenantUserDetailsService users,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.OAuthClientRepository> clients,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSettingsService> settings,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.tenant.TenantRepository> tenants) {
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        if (!idToken && !accessToken) {
            return;
        }
        java.util.UUID clientRowId;
        try {
            clientRowId = java.util.UUID.fromString(context.getRegisteredClient().getId());
        } catch (IllegalArgumentException e) {
            return;
        }
        java.util.UUID applicationId = com.secureone.auth.tenant.RlsScope.asPlatform(() -> clients.getObject()
                .findById(clientRowId).map(com.secureone.auth.oauth.OAuthClient::getApplicationId).orElse(null));
        if (applicationId == null) {
            return;
        }
        List<com.secureone.auth.application.TokenClaimMappings.Claim> claims =
                com.secureone.auth.tenant.RlsScope.asPlatform(() -> settings.getObject().resolveTokenClaims(applicationId));
        if (claims.isEmpty()) {
            return;
        }
        UserAccount user = null;
        String tenantSlug = null;
        boolean userToken = !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                && context.getPrincipal() != null;
        if (userToken) {
            String name = context.getPrincipal().getName();
            int sep = name == null ? -1 : name.indexOf(':');
            if (sep > 0) {
                tenantSlug = name.substring(0, sep);
                try {
                    user = users.resolveAccount(tenantSlug, name.substring(sep + 1));
                } catch (UsernameNotFoundException ignored) {
                    // No user: only fixed values apply.
                }
            }
        }
        for (var claim : claims) {
            if ((idToken && !claim.idToken()) || (accessToken && !claim.accessToken())) {
                continue;
            }
            Object value = com.secureone.auth.application.TokenClaimMappings.valueFor(claim, user, tenantSlug);
            if (value != null) {
                context.getClaims().claims(map -> map.putIfAbsent(claim.name(), value));
            }
        }
    }

    /**
     * Refuse tokens for a user who is no longer active. Spring's refresh-token grant, and an
     * authorization request riding on an existing browser session, never reload the user, so without
     * this a suspended, disabled or deleted account keeps getting fresh access tokens until its refresh
     * token expires. Checked once per request, on the access token. Principals are "tenant-slug:email".
     */
    /**
     * Standard OIDC claims for the scopes granted, plus {@code user_id}: the account's UUID, which,
     * unlike {@code sub} ("tenant:email"), does not change when the user changes email. Apps should key
     * their users on it. In the ID token (and served by /userinfo), and in user access tokens too, so an
     * API can tell who is calling without a round trip ({@code sub} already carries the email).
     * Client-credentials tokens have no user and get none of these.
     */
    private static void addProfileClaims(JwtEncodingContext context, TenantUserDetailsService users) {
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        boolean userAccessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                && !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType());
        if (!(idToken || userAccessToken) || context.getPrincipal() == null) {
            return;
        }
        String name = context.getPrincipal().getName();
        int sep = name == null ? -1 : name.indexOf(':');
        if (sep <= 0 || !name.contains("@")) {
            return;
        }
        UserAccount user;
        try {
            user = users.resolveAccount(name.substring(0, sep), name.substring(sep + 1));
        } catch (UsernameNotFoundException ex) {
            return;
        }
        var scopes = context.getAuthorizedScopes();
        context.getClaims().claim("user_id", user.getId().toString());
        if (scopes.contains("email")) {
            context.getClaims().claim("email", user.getEmail());
            context.getClaims().claim("email_verified", user.isEmailVerified());
        }
        if (scopes.contains("profile")) {
            String display = user.getDisplayName();
            if (display != null && !display.isBlank()) {
                context.getClaims().claim("name", display);
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                context.getClaims().claim("preferred_username", user.getUsername());
            }
        }
    }

    private static void requireActiveUser(JwtEncodingContext context, TenantUserDetailsService users) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                || AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                || context.getPrincipal() == null) {
            return;
        }
        String name = context.getPrincipal().getName();
        int sep = name == null ? -1 : name.indexOf(':');
        if (sep <= 0 || !name.contains("@")) {
            return;
        }
        String status;
        try {
            status = users.tokenStatus(name.substring(0, sep), name.substring(sep + 1));
        } catch (UsernameNotFoundException deleted) {
            status = "DELETED";
        }
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "The user account is not active.", null));
        }
    }

    /**
     * The user's roles and permissions in the client's application, so the app can authorise without
     * calling back: {@code roles} (direct, via groups, composites expanded) and {@code permissions}.
     * User tokens only; client-credentials tokens have no user.
     */
    private static void addApplicationAccess(JwtEncodingContext context, ApplicationAccessClaims accessClaims) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                || AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                || context.getPrincipal() == null) {
            return;
        }
        ApplicationAccessClaims.Access access =
                accessClaims.forClient(context.getRegisteredClient().getClientId(), context.getPrincipal().getName());
        context.getClaims().claim("roles", access.roles());
        context.getClaims().claim("permissions", access.permissions());
    }

    /**
     * Token policy lifetimes Spring has no setting for: client-credentials access tokens and ID tokens.
     * The response's expires_in follows the token's exp, so setting exp is enough.
     */
    private static void applyPolicyLifetime(JwtEncodingContext context) {
        var settings = context.getRegisteredClient().getTokenSettings();
        Object ttl = null;
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                && AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            ttl = settings.getSetting(TokenSettingsMapper.CLIENT_CREDENTIALS_TTL);
        } else if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            ttl = settings.getSetting(TokenSettingsMapper.ID_TOKEN_TTL);
        }
        if (ttl instanceof Duration lifetime && !lifetime.isZero()) {
            context.getClaims().claims(claims -> {
                Instant issuedAt = claims.get("iat") instanceof Instant iat ? iat : Instant.now();
                claims.put("exp", issuedAt.plus(lifetime));
            });
        }
    }
}
