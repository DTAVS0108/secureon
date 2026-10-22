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
 *   <li>OIDC {@code max_age} on {@code /oauth2/authorize}: a sign-in older than that is ended and the user signs in
 *       again.</li>
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

    public SignInFreshnessFilter(
            @Value("${secureone.session.max-lifetime:24h}") Duration maxLifetime,
            ObjectProvider<MfaLoginService> mfaLogin,
            ObjectProvider<OAuthClientRepository> clients) {
        this.maxLifetime = maxLifetime;
        this.mfaLogin = mfaLogin;
        this.clients = clients;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication auth = session != null ? signedIn(session) : null;
        if (auth == null) {
            chain.doFilter(request, response);
            return;
        }
        Instant signedInAt = AuthContextAttributes.authTime(request);
        if (signedInAt != null && signedInAt.plus(maxLifetime).isBefore(Instant.now())) {
            session.invalidate(); // absolute lifetime reached: continue as signed out
            chain.doFilter(request, response);
            return;
        }
        if ("GET".equals(request.getMethod()) && "/oauth2/authorize".equals(request.getRequestURI())) {
            String here = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String maxAge = request.getParameter("max_age");
            if (maxAge != null && signedInAt != null && tooOld(signedInAt, maxAge)) {
                session.invalidate();
                response.sendRedirect("/login.html?continue=" + encode(here));
                return;
            }
            String acrValues = request.getParameter("acr_values");
            if (acrValues != null && AuthContextAttributes.acrLevel(request) < 2 && wantsTwoSteps(acrValues)) {
                UUID applicationId = applicationOf(request.getParameter("client_id"));
                UUID challenge = mfaLogin.getObject().issueStepUp(auth, applicationId);
                if (challenge != null) {
                    response.sendRedirect("/login.html?mfa=1&mfaChallengeId=" + challenge
                            + (applicationId != null ? "&applicationId=" + applicationId : "")
                            + "&continue=" + encode(here));
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    static boolean wantsTwoSteps(String acrValues) {
        return Arrays.stream(acrValues.trim().split("\\s+")).anyMatch(TWO_STEP_ACR::contains);
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
