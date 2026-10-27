package com.secureone.auth.authn;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.config.oauth.DeviceCodeLookup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.util.UriComponentsBuilder;

/** Sends users to the hosted login page while preserving the OAuth authorize (or device verification) URL. */
public class OAuthAwareLoginEntryPoint implements AuthenticationEntryPoint {

    private final RequestCache requestCache;
    private final ApplicationOAuthClientResolver applications;
    private final DeviceCodeLookup deviceCodes;

    public OAuthAwareLoginEntryPoint(
            RequestCache requestCache, ApplicationOAuthClientResolver applications, DeviceCodeLookup deviceCodes) {
        this.requestCache = requestCache;
        this.applications = applications;
        this.deviceCodes = deviceCodes;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // prompt=login is met by the sign-in this sends the user to: they come back without it (no second prompt).
        HttpServletRequest returnTo = OAuthLoginRedirectSupport.withoutPromptLoginRequest(request);
        String authorizeUrl = OAuthLoginRedirectSupport.buildAuthorizeUrl(returnTo);
        if (OAuthLoginRedirectSupport.isAuthorizeUrl(authorizeUrl)) {
            requestCache.saveRequest(returnTo, response);
            String loginUrl = appendApplicationId(
                    OAuthLoginRedirectSupport.loginPageWithContinue(authorizeUrl), authorizeUrl);
            response.sendRedirect(loginUrl);
            return;
        }
        response.sendRedirect("/login.html");
    }

    private String appendApplicationId(String loginUrl, String authorizeUrl) {
        // Append without re-parsing: loginUrl carries the whole authorize URL in its continue
        // parameter, which strict "already encoded" parsing rejects (it contains '=').
        return resolveApplicationId(authorizeUrl)
                .map(appId -> loginUrl + (loginUrl.contains("?") ? "&" : "?") + "applicationId=" + appId)
                .orElse(loginUrl);
    }

    private java.util.Optional<java.util.UUID> resolveApplicationId(String authorizeUrl) {
        String userCode = queryParam(authorizeUrl, "user_code");
        if (userCode != null) {
            // Device verification: the app is the one the device code was issued to.
            return deviceCodes.pending(userCode).map(DeviceCodeLookup.PendingDevice::applicationId);
        }
        String clientId = queryParam(authorizeUrl, "client_id");
        return applications.findActiveByOAuthClientId(clientId).map(Application::getId);
    }

    private static String queryParam(String authorizeUrl, String name) {
        if (authorizeUrl == null || authorizeUrl.isBlank()) {
            return null;
        }
        try {
            return UriComponentsBuilder.fromUriString(authorizeUrl)
                    .build(true)
                    .getQueryParams()
                    .getFirst(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
