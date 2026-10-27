package com.secureone.auth.oauth;

import com.secureone.auth.account.UserAccessTermination;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * After an application's RP-initiated logout ({@code /connect/logout}): the grants given during that browser
 * session end (its refresh tokens stop working; {@code offline_access} ones are kept), and the other applications
 * are told too.
 */
public final class BackchannelNotifyingLogoutHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(BackchannelNotifyingLogoutHandler.class);

    private final AuthenticationSuccessHandler next;
    private final BackchannelLogoutService backchannel;
    private final UserAccessTermination access;

    public BackchannelNotifyingLogoutHandler(
            AuthenticationSuccessHandler next, BackchannelLogoutService backchannel, UserAccessTermination access) {
        this.next = next;
        this.backchannel = backchannel;
        this.access = access;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication instanceof OidcLogoutAuthenticationToken logout
                && logout.getPrincipal() instanceof Authentication user) {
            try {
                String principal = user.getName();
                // Which applications to tell is read before the session's grants go.
                List<OAuthClient> clients = RlsScope.asPlatform(() -> backchannel.clientsToNotify(principal));
                String sid = logout.getIdToken() != null ? logout.getIdToken().getClaimAsString("sid") : null;
                access.endBrowserSession(principal, sid);
                // The session ID from the id_token_hint goes in the logout tokens too (OIDC Back-Channel Logout sid).
                backchannel.sessionSignedOut(principal, logout.getSessionId(), clients);
            } catch (RuntimeException e) {
                log.warn("Sign-out of {} not fully completed: {}", user.getName(), e.getMessage());
            }
        }
        next.onAuthenticationSuccess(request, response, authentication);
    }
}
