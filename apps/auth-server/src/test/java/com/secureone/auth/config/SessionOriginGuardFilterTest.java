package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SessionOriginGuardFilterTest {

    private SessionOriginGuardFilter filter;

    @BeforeEach
    void setUp() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("https://app.tenant-a.test");
        UrlBasedCorsConfigurationSource cors = new UrlBasedCorsConfigurationSource();
        cors.registerCorsConfiguration("/**", config);
        filter = new SessionOriginGuardFilter(cors, "https://login.tenant-a.test");
    }

    @Test
    void crossOriginPostWithCookieIsBlocked() throws Exception {
        MockHttpServletResponse response = run(post("/api/v1/account/privacy/session/delete-request", "https://evil.test"));

        assertEquals(403, response.getStatus());
    }

    @Test
    void sameOriginPostPasses() throws Exception {
        assertPassed(run(post("/api/v1/account/privacy/session/delete-request", "http://localhost:9000")));
    }

    @Test
    void publicBaseUrlOriginPasses() throws Exception {
        assertPassed(run(post("/login", "https://login.tenant-a.test")));
    }

    @Test
    void allowedCorsOriginPasses() throws Exception {
        assertPassed(run(post("/api/v1/account/sessions/revoke", "https://app.tenant-a.test")));
    }

    @Test
    void refererIsUsedWhenOriginIsMissing() throws Exception {
        MockHttpServletRequest request = post("/api/v1/account/mfa/factors", null);
        request.addHeader("Referer", "https://evil.test/page?x=1");

        assertEquals(403, run(request).getStatus());
    }

    @Test
    void nullOriginIsBlocked() throws Exception {
        assertEquals(403, run(post("/api/v1/account/mfa/factors", "null")).getStatus());
    }

    @Test
    void requestWithoutOriginOrRefererPasses() throws Exception {
        assertPassed(run(post("/api/v1/account/mfa/factors", null)));
    }

    @Test
    void requestWithoutCookiesIsNotChecked() throws Exception {
        MockHttpServletRequest request = post("/api/v1/account/mfa/factors", "https://evil.test");
        request.setCookies();

        assertPassed(run(request));
    }

    @Test
    void safeMethodsAreNotChecked() throws Exception {
        MockHttpServletRequest request = post("/api/v1/account/sessions", "https://evil.test");
        request.setMethod("GET");

        assertPassed(run(request));
    }

    @Test
    void federationCallbacksAreExempt() throws Exception {
        assertPassed(run(post("/api/v1/auth/federation/callback/saml", "https://idp.partner.test")));
    }

    @Test
    void originOfParsesUrls() {
        assertEquals("https://a.test:8443", SessionOriginGuardFilter.originOf("HTTPS://A.test:8443/x?y"));
        assertEquals("http://a.test", SessionOriginGuardFilter.originOf("http://a.test"));
        assertNull(SessionOriginGuardFilter.originOf("not a url"));
        assertNull(SessionOriginGuardFilter.originOf(""));
    }

    private static MockHttpServletRequest post(String path, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(9000);
        request.setCookies(new Cookie("SESSION", "abc"));
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        response.setHeader("X-Chain-Reached", chain.getRequest() != null ? "yes" : "no");
        return response;
    }

    private static void assertPassed(MockHttpServletResponse response) {
        assertEquals(200, response.getStatus());
        assertNotNull(response.getHeader("X-Chain-Reached"));
        assertEquals("yes", response.getHeader("X-Chain-Reached"));
    }
}
