package com.secureone.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/** After an application's RP-initiated logout ({@code /connect/logout}), tells the other applications too. */
public final class BackchannelNotifyingLogoutHandler implements AuthenticationSuccessHandler {

    private final AuthenticationSuccessHandler next;
    private final BackchannelLogoutService backchannel;

    public BackchannelNotifyingLogoutHandler(AuthenticationSuccessHandler next, BackchannelLogoutService backchannel) {
        this.next = next;
        this.backchannel = backchannel;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication instanceof OidcLogoutAuthenticationToken logout
                && logout.getPrincipal() instanceof Authentication user) {
            // The session ID from the id_token_hint goes in the logout tokens too (OIDC Back-Channel Logout sid).
            backchannel.signedOut(user.getName(), logout.getSessionId());
        }
        next.onAuthenticationSuccess(request, response, authentication);
    }
}
