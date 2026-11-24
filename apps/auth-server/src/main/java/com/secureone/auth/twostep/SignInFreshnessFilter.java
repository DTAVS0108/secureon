package com.secureone.auth.twostep;

import com.secureone.auth.authn.AuthContextAttributes;
import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * How fresh and how strong an end-user browser session must be.
 * <ul>
 *   <li>Absolute lifetime: a session ends {@code secureone.session.max-lifetime} after its sign-in (default 24 h,
 *       NIST SP 800-63B AAL2), whatever the activity.</li>
 *   <li>OIDC {@code max_age} on {@code /oauth2/authorize}, and the application's own "sign in again after"
 *       ({@code signInMaxAgeSeconds} in its OAuth token policy): a sign-in older than that is ended and the user
 *       signs in again.</li>
 *   <li>OIDC {@code acr_values} asking for two steps ({@code 2}, or the MFA URNs): a one-step session goes through
 *       the second step first (when the user has an option; otherwise the token says {@code acr=1}).</li>
 * </ul>
 * Runs after Spring Session and before Spring Security (registered in {@link TwoStepConfig}).
 */
public class SignInFreshnessFilter extends OncePerRequestFilter {

    static final List<String> TWO_STEP_ACR = List.of(
            "2", "urn:secureone:acr:2", "urn:secureone:acr:mfa",
            "http://schemas.openid.net/pape/policies/2007/06/multi-factor");

    private final Duration maxLifetime;
    private final ObjectProvider<MfaLoginService> mfaLogin;
    private final ObjectProvider<OAuthClientRepository> clients;
    private final ObjectProvider<com.secureone.auth.application.ApplicationSettingsService> applicationSettings;

    public SignInFreshnessFilter(
            @Value("${secureone.session.max-lifetime:24h}") Duration maxLifetime,
            ObjectProvider<MfaLoginService> mfaLogin,
            ObjectProvider<OAuthClientRepository> clients,
            ObjectProvider<com.secureone.auth.application.ApplicationSettingsService> applicationSettings) {
        this.maxLifetime = maxLifetime;
        this.mfaLogin = mfaLogin;
        this.clients = clients;
        this.applicationSettings = applicationSettings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication auth = session != null ? signedIn(session) : null;
        boolean authorize = "GET".equals(request.getMethod()) && "/oauth2/authorize".equals(request.getRequestURI());
        Set<String> prompt = authorize ? promptValues(request.getParameter("prompt")) : Set.of();
        if (auth == null) {
            // prompt=none and nobody signed in: tell the app (silent sign-in), never show the sign-in page.
            if (prompt.contains("none") && loginRequired(request, response)) {
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        Instant signedInAt = AuthContextAttributes.authTime(request);
        if (signedInAt != null && signedInAt.plus(maxLifetime).isBefore(Instant.now())) {
            session.invalidate(); // absolute lifetime reached: continue as signed out
            chain.doFilter(request, response);
            return;
        }
        if (authorize) {
            // Prefix kept: a tenant issuer's request (/t/<slug>/oauth2/authorize) comes back to that issuer.
            String here = com.secureone.auth.authn.OAuthLoginRedirectSupport.buildAuthorizeUrl(request);
            if (prompt.contains("login") && !prompt.contains("none")) {
                // prompt=login: sign in again; the request continues without "login" so it does not loop (the
                // sign-in page for a signed-out user gets it without "login" too: OAuthAwareLoginEntryPoint).
                session.invalidate();
                response.sendRedirect("/login.html?continue=" + encode(withoutPromptLogin(request)));
                return;
            }
            String maxAge = request.getParameter("max_age");
            if (signedInAt != null && (maxAge != null && tooOld(signedInAt, maxAge)
                    || tooOld(signedInAt, String.valueOf(applicationMaxAge(request.getParameter("client_id")))))) {
                session.invalidate();
                if (prompt.contains("none") && loginRequired(request, response)) {
                    return;
                }
                response.sendRedirect("/login.html?continue=" + encode(here));
                return;
            }
            // prompt=none naming another user (id_token_hint) than the one signed in: no silent code for the wrong
            // person (OIDC Core 3.1.2.1). The hint is only read to refuse, never to sign anyone in.
            String hint = request.getParameter("id_token_hint");
            if (prompt.contains("none") && hint != null && !hint.isBlank() && !hintMatches(hint, auth)
                    && oauthError(request, response, "login_required",
                            "The signed-in user is not the one named by id_token_hint.")) {
                return;
            }
            UUID applicationId = applicationOf(request.getParameter("client_id"));
            String acrValues = request.getParameter("acr_values");
            if (acrValues != null && AuthContextAttributes.acrLevel(request) < 2 && wantsTwoSteps(acrValues)) {
                UUID challenge = mfaLogin.getObject().issueStepUp(auth, applicationId);
                if (challenge != null) {
                    if (prompt.contains("none") && oauthError(request, response, "interaction_required",
                            "The user must complete two-step verification; prompt=none shows no page.")) {
                        return;
                    }
                    response.sendRedirect(secondStepPage(challenge, applicationId, here));
                    return;
                }
            }
            // Single sign-on into an application this session did not sign in to: that application's own
            // two-step rule applies (a session from an application without two-step cannot skip it here).
            if (applicationId != null && AuthContextAttributes.acrLevel(request) < 2
                    && !MfaLoginService.decidedFor(request, auth, applicationId)) {
                UUID challenge = mfaLogin.getObject().issueForApplication(auth, applicationId, request);
                if (challenge != null) {
                    if (prompt.contains("none") && oauthError(request, response, "interaction_required",
                            "This application requires two-step verification; prompt=none shows no page.")) {
                        return;
                    }
                    response.sendRedirect(secondStepPage(challenge, applicationId, here));
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    static boolean wantsTwoSteps(String acrValues) {
        return Arrays.stream(acrValues.trim().split("\\s+")).anyMatch(TWO_STEP_ACR::contains);
    }

    /** The application's "sign in again after" in seconds; -1 when it has none (or it cannot be read). */
    private long applicationMaxAge(String clientId) {
        UUID applicationId = applicationOf(clientId);
        if (applicationId == null) {
            return -1;
        }
        try {
            Object raw = RlsScope.asPlatform(
                    () -> applicationSettings.getObject().resolveTokenPolicy(applicationId).get("signInMaxAgeSeconds"));
            long seconds = raw instanceof Number n ? n.longValue() : 0;
            return seconds > 0 ? seconds : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static Set<String> promptValues(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Set.of();
        }
        return new java.util.HashSet<>(Arrays.asList(prompt.trim().split("\\s+")));
    }

    /**
     * OIDC Core 3.1.2.6: {@code error=login_required} back to the app, only to a redirect URI registered for the
     * client (exact match), with the state. False when the request cannot be answered that way (unknown client
     * or redirect URI): the authorization server then explains the error itself.
     */
    private boolean loginRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return oauthError(request, response, "login_required", "The user must sign in; prompt=none does not show the sign-in page.");
    }

    /**
     * Whether an ID token hint names the signed-in user: its user_id (or its sub: the user id, or "tenant:login" for
     * clients with the legacy subject). An unreadable hint is not judged here (the authorization server checks it).
     */
    boolean hintMatches(String idTokenHint, Authentication auth) {
        Map<String, Object> claims = unverifiedClaims(idTokenHint);
        if (claims == null) {
            return true;
        }
        Object hinted = claims.get("user_id") != null ? claims.get("user_id") : claims.get("sub");
        if (!(hinted instanceof String who) || who.isBlank()) {
            return true;
        }
        if (who.equalsIgnoreCase(auth.getName())) {
            return true;
        }
        try {
            UUID current = mfaLogin.getObject().userIdOf(auth);
            return current != null && who.equals(current.toString());
        } catch (RuntimeException e) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> unverifiedClaims(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] json = java.util.Base64.getUrlDecoder().decode(parts[1]);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String secondStepPage(UUID challenge, UUID applicationId, String continueUrl) {
        return "/login.html?mfa=1&mfaChallengeId=" + challenge
                + (applicationId != null ? "&applicationId=" + applicationId : "")
                + "&continue=" + encode(continueUrl);
    }

    /** An OIDC error back to the app (see {@link #loginRequired}). */
    private boolean oauthError(HttpServletRequest request, HttpServletResponse response, String error, String description)
            throws IOException {
        String clientId = request.getParameter("client_id");
        String redirectUri = request.getParameter("redirect_uri");
        if (clientId == null || redirectUri == null || redirectUri.isBlank()) {
            return false;
        }
        boolean registered = Boolean.TRUE.equals(RlsScope.asPlatform(() -> clients.getObject().findByClientId(clientId)
                .map(c -> c.getRedirectUris().stream().anyMatch(u -> u != null && u.trim().equals(redirectUri)))
                .orElse(false)));
        if (!registered) {
            return false;
        }
        StringBuilder target = new StringBuilder(redirectUri)
                .append(redirectUri.contains("?") ? '&' : '?')
                .append("error=").append(error).append("&error_description=")
                .append(encode(description));
        String state = request.getParameter("state");
        if (state != null && !state.isEmpty()) {
            target.append("&state=").append(encode(state));
        }
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(target.toString());
        return true;
    }

    /** This authorize request with "login" taken out of prompt (and prompt dropped when nothing is left). */
    static String withoutPromptLogin(HttpServletRequest request) {
        return com.secureone.auth.authn.OAuthLoginRedirectSupport.withoutPromptLogin(request);
    }

    static boolean tooOld(Instant signedInAt, String maxAge) {
        try {
            long seconds = Long.parseLong(maxAge.trim());
            return seconds >= 0 && signedInAt.plusSeconds(seconds).isBefore(Instant.now());
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private UUID applicationOf(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return RlsScope.asPlatform(() -> clients.getObject().findByClientId(clientId)
                .map(c -> c.getApplicationId())
                .orElse(null));
    }

    private static Authentication signedIn(HttpSession session) {
        Object raw = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(raw instanceof SecurityContext context)) {
            return null;
        }
        Authentication auth = context.getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) ? auth : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
