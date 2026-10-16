package com.secureone.auth.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secureone.auth.config.oauth.DeviceCodeLookup;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OAuthLoginRedirectSupportTest {

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServerName("auth.example.com");
        request.setServerPort(443);
        request.setScheme("https");
        return request;
    }

    @Test
    void continueMustBeAnOAuthEndpointOnThisServer() {
        MockHttpServletRequest request = request();
        assertTrue(OAuthLoginRedirectSupport.isSafeContinue(request, "/oauth2/authorize?client_id=a"));
        assertTrue(OAuthLoginRedirectSupport.isSafeContinue(request, "/oauth2/device_verification?user_code=BCDF-GHJK"));
        assertTrue(OAuthLoginRedirectSupport.isSafeContinue(request, "https://auth.example.com/oauth2/authorize?client_id=a"));

        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "https://evil.example/oauth2/authorize?client_id=a"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "//evil.example/oauth2/authorize"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "/\\evil.example/oauth2/authorize"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "javascript:alert(1)//oauth2/authorize"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "https://auth.example.com:8443/oauth2/authorize"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "/oauth2/authorize-not/x"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "/account/profile"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, null));
    }

    @Test
    void readContinueParamDropsOffSiteValues() {
        MockHttpServletRequest request = request();
        request.setParameter("continue", "https://evil.example/oauth2/authorize");
        assertNull(OAuthLoginRedirectSupport.readContinueParam(request));
        request.setParameter("continue", "/oauth2/authorize?client_id=a");
        assertEquals("/oauth2/authorize?client_id=a", OAuthLoginRedirectSupport.readContinueParam(request));
    }

    @Test
    void entryPointBuildsRelativeUrls() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=a&state=s");
        assertEquals("/oauth2/authorize?client_id=a&state=s", OAuthLoginRedirectSupport.buildAuthorizeUrl(request));
    }

    @Test
    void deviceUserCodesAreNormalized() {
        assertEquals("WDJB-MJHT", DeviceCodeLookup.normalize("wdjb-mjht"));
        assertEquals("WDJB-MJHT", DeviceCodeLookup.normalize(" WDJBMJHT "));
        assertNull(DeviceCodeLookup.normalize("WDJB-MJH"));
        assertNull(DeviceCodeLookup.normalize(null));
    }
}
