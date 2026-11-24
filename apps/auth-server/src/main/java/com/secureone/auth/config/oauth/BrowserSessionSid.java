package com.secureone.auth.config.oauth;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The browser session an authorization code was issued in, for the ID token's {@code sid} claim.
 *
 * <p>Spring Authorization Server fills {@code sid} with the user's most recently used session, which is another
 * device's session when the user is signed in on several: logout with that ID token is then refused, and
 * back-channel logout would name the wrong browser. SecureOne records the session of the authorize request on the
 * authorization (when the code is issued) and the token customizer puts that one in every ID token issued from it.
 * The value is hashed the way Spring Authorization Server hashes it, so its logout check accepts it.
 */
public final class BrowserSessionSid {

    /** Authorization attribute holding the hashed session id. */
    public static final String ATTRIBUTE = "secureone.browser_sid";

    private BrowserSessionSid() {}

    /** SHA-256, base64url without padding: as Spring Authorization Server hashes session ids for {@code sid}. */
    public static String hash(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** The hashed id of the current request's browser session, or null outside a request or without a session. */
    static String currentRequestSid() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpSession session = attrs.getRequest().getSession(false);
        return session != null ? hash(session.getId()) : null;
    }

    /**
     * The authorization with the current browser session recorded, when it is being saved with a fresh
     * authorization code (the authorize request) and has none yet; otherwise the authorization unchanged.
     */
    static OAuth2Authorization withBrowserSession(OAuth2Authorization authorization) {
        if (authorization == null
                || authorization.getAttribute(ATTRIBUTE) != null
                || authorization.getAccessToken() != null
                || authorization.getToken(
                                org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode.class)
                        == null) {
            return authorization;
        }
        String sid = currentRequestSid();
        if (sid == null || isTokenRequest()) {
            return authorization;
        }
        return OAuth2Authorization.from(authorization).attribute(ATTRIBUTE, sid).build();
    }

    /** The token endpoint has no browser session of the user (a client may still send a cookie): never record it. */
    private static boolean isTokenRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return false;
        }
        return attrs.getRequest().getParameter(OAuth2ParameterNames.GRANT_TYPE) != null;
    }
}
