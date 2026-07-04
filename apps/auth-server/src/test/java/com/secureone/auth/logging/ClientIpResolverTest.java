package com.secureone.auth.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void prefersFirstForwardedForIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.setRemoteAddr("127.0.0.1");

        assertEquals("203.0.113.10", ClientIpResolver.resolve(request));
    }

    @Test
    void fallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.4");
        request.setRemoteAddr("127.0.0.1");

        assertEquals("198.51.100.4", ClientIpResolver.resolve(request));
    }

    @Test
    void parsesForwardedChain() {
        assertEquals("203.0.113.1", ClientIpResolver.firstForwardedIp("203.0.113.1, 10.0.0.2"));
        assertNull(ClientIpResolver.firstForwardedIp("  "));
    }
}
