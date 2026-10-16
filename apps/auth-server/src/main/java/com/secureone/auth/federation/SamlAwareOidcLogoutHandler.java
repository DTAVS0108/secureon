package com.secureone.auth.federation;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * OIDC RP-initiated logout ({@code /connect/logout}) that also signs the user out at their SAML IdP when
 * the session came from one: the validated post_logout_redirect_uri (and state) is kept on the session,
 * then the SAML logout endpoint ends the session and goes through the IdP before returning there.
 */
public final class SamlAwareOidcLogoutHandler implements AuthenticationSuccessHandler {

    private final OidcLogoutAuthenticationSuccessHandler standard = new OidcLogoutAuthenticationSuccessHandler();
    private final SamlLogoutService samlLogout;

    public SamlAwareOidcLogoutHandler(SamlLogoutService samlLogout) {
        this.samlLogout = samlLogout;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication instanceof OidcLogoutAuthenticationToken logout) || !samlLogout.hasSamlSession(request)) {
            standard.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        String target = SamlLogoutService.SIGNED_OUT_PAGE;
        if (logout.getPostLogoutRedirectUri() != null && !logout.getPostLogoutRedirectUri().isBlank()) {
            // Spring has already checked it against the client's registered post-logout URIs.
            target = logout.getPostLogoutRedirectUri();
            if (logout.getState() != null && !logout.getState().isBlank()) {
                target += (target.contains("?") ? "&" : "?") + "state="
                        + java.net.URLEncoder.encode(logout.getState(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        request.getSession().setAttribute(SamlLogoutService.SESSION_LOGOUT_CONTINUE, target);
        response.sendRedirect("/api/v1/auth/federation/saml/logout");
    }
}
