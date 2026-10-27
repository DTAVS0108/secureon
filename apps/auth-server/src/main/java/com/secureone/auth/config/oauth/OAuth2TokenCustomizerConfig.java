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
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.tenant.TenantRepository> tenants,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationApiService> apis) {
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
            if (amr.isEmpty() && isUserToken(context)) {
                // Token endpoint (no browser session): how the user signed in, from the stored principal.
                amr = AuthContextAttributes.amrOf(context.getPrincipal());
                acr = AuthContextAttributes.acrOf(context.getPrincipal());
            }
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType()) && context.getRegisteredClient() != null) {
                // RFC 9068: JWT access tokens name the client they were issued to.
                context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
            }
            if (!amr.isEmpty()) {
                context.getClaims().claim("amr", amr);
            }
            if (acr != null && !acr.isBlank() && !"0".equals(acr)) {
                context.getClaims().claim("acr", acr);
            }
            applyPolicyLifetime(context);
            addApplicationAccess(context, accessClaims);
            addApiAudiences(context, clients, apis);
            addApplicationClaims(context, users, clients, settings, tenants);
        };
    }

    /**
     * Access tokens name the application's APIs they are for in {@code aud}, next to the client: every API one of
     * whose scopes was granted, or only the API the client named with RFC 8707 {@code resource} (on the
     * authorization request or the token request). A {@code resource} that is not one of the application's APIs
     * is refused with {@code invalid_target}.
     */
    private static void addApiAudiences(
            JwtEncodingContext context,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.OAuthClientRepository> clients,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationApiService> apis) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType()) || context.getRegisteredClient() == null) {
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
        String resource = requestedResource(context);
        java.util.Optional<List<String>> audiences =
                apis.getObject().audiences(applicationId, context.getAuthorizedScopes(), resource);
        if (audiences.isEmpty()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_target",
                    "resource '" + resource + "' is not one of this application's APIs.",
                    "https://datatracker.ietf.org/doc/html/rfc8707#section-2"));
        }
        if (audiences.get().isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> aud = new java.util.LinkedHashSet<>(audiences.get());
        aud.add(context.getRegisteredClient().getClientId());
        // ArrayList, not List.copyOf: the claims are stored with the authorization (see AuthContextAttributes.amrOf).
        context.getClaims().audience(new java.util.ArrayList<>(aud));
    }

    /** RFC 8707 resource: from the token request, else from the authorization request this token comes from. */
    private static String requestedResource(JwtEncodingContext context) {
        var attrs = (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String fromRequest = attrs != null ? attrs.getRequest().getParameter("resource") : null;
        if (fromRequest != null && !fromRequest.isBlank()) {
            return fromRequest.trim();
        }
        if (context.getAuthorization() != null) {
            org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest authorizationRequest =
                    context.getAuthorization().getAttribute(
                            org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName());
            if (authorizationRequest != null
                    && authorizationRequest.getAdditionalParameters().get("resource") instanceof String r
                    && !r.isBlank()) {
                return r.trim();
            }
        }
        return null;
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

    /** A token for a signed-in user ("tenant-slug:email" principal), not a client-credentials token. */
    private static boolean isUserToken(JwtEncodingContext context) {
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                || context.getPrincipal() == null) {
            return false;
        }
        String name = context.getPrincipal().getName();
        return name != null && name.indexOf(':') > 0 && name.contains("@");
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
