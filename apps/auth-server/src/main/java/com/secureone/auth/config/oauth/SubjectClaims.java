package com.secureone.auth.config.oauth;

import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.user.UserAccount;
import java.util.Optional;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

/**
 * The token subject a client receives. Internally a user is "tenant-slug:email" (the session
 * principal); clients set to {@code user_id} get the account UUID as {@code sub} instead, which does
 * not change when the user changes email. Every user access token also carries {@code user_id}.
 */
public final class SubjectClaims {

    /** Client setting holding {@link OAuthClient#SUBJECT_USER_ID} or {@link OAuthClient#SUBJECT_LEGACY}. */
    public static final String SETTING = "secureone.subject-type";

    private SubjectClaims() {}

    static void apply(JwtEncodingContext context, TenantUserDetailsService users) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if ((!accessToken && !idToken)
                || AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                || context.getPrincipal() == null) {
            return;
        }
        Optional<UserAccount> user = resolve(context.getPrincipal().getName(), users);
        if (user.isEmpty()) {
            return;
        }
        String userId = user.get().getId().toString();
        context.getClaims().claim("user_id", userId);
        // Scopes bearer API requests to the user's tenant (see BearerTenantContextFilter).
        context.getClaims().claim("tenant_id", user.get().getTenantId().toString());
        Object type = context.getRegisteredClient().getClientSettings().getSetting(SETTING);
        if (OAuthClient.SUBJECT_USER_ID.equals(type)) {
            context.getClaims().subject(userId);
        }
    }

    /** "tenant-slug:email" principal → account. */
    public static Optional<UserAccount> resolve(String principalName, TenantUserDetailsService users) {
        int sep = principalName == null ? -1 : principalName.indexOf(':');
        if (sep <= 0 || !principalName.contains("@")) {
            return Optional.empty();
        }
        try {
            return Optional.of(users.resolveAccount(principalName.substring(0, sep), principalName.substring(sep + 1)));
        } catch (UsernameNotFoundException ex) {
            return Optional.empty();
        }
    }
}
