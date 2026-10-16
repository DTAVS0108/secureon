package com.secureone.auth.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void usesTheContainerClientAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        assertEquals("203.0.113.10", ClientIpResolver.resolve(request));
    }

    @Test
    void ignoresForwardedHeadersSentByTheClient() {
        // Trusted proxies are handled by the container before the request gets here; a header the
        // client sent itself must not change the address (or the rate-limit bucket).
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("X-Real-IP", "198.51.100.98");
        request.setRemoteAddr("203.0.113.10");

        assertEquals("203.0.113.10", ClientIpResolver.resolve(request));
    }
}
