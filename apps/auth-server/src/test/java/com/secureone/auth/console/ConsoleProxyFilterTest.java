package com.secureone.auth.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConsoleProxyFilterTest {

    private HttpServer console;
    private final AtomicReference<Map<String, List<String>>> seenHeaders = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private final AtomicReference<String> seenPath = new AtomicReference<>();

    @BeforeEach
    void startConsole() throws IOException {
        console = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        console.createContext("/", exchange -> {
            seenHeaders.set(Map.copyOf(exchange.getRequestHeaders()));
            seenPath.set(exchange.getRequestURI().toString());
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (exchange.getRequestURI().getPath().equals("/admin/tenants")) {
                exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port() + "/admin/login?next=%2Ftenants");
                exchange.getResponseHeaders().add("Set-Cookie", "a=1; Path=/");
                exchange.getResponseHeaders().add("Set-Cookie", "b=2; Path=/");
                exchange.sendResponseHeaders(307, -1);
            } else {
                byte[] page = "<html>console</html>".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, page.length);
                exchange.getResponseBody().write(page);
            }
            exchange.close();
        });
        console.start();
    }

    @AfterEach
    void stopConsole() {
        console.stop(0);
    }

    private int port() {
        return console.getAddress().getPort();
    }

    private ConsoleProxyFilter filter(String upstream) {
        return new ConsoleProxyFilter(new ConsoleSettings(upstream, "", 3001, "node"));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServerName("id.example.com");
        request.setServerPort(443);
        request.setScheme("https");
        request.setSecure(true);
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("Host", "id.example.com");
        return request;
    }

    @Test
    void forwardsAdminPagesAndAddsSecurityHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/admin/login");
        request.setQueryString("reason=expired");

        filter("http://127.0.0.1:" + port()).doFilter(request, response, chain);

        assertNull(chain.getRequest(), "the rest of the chain (Spring Security, MVC) must not run for /admin");
        assertEquals(200, response.getStatus());
        assertEquals("<html>console</html>", response.getContentAsString());
        assertEquals("/admin/login?reason=expired", seenPath.get());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertTrue(response.getHeader("Strict-Transport-Security").startsWith("max-age="));
    }

    @Test
    void replacesClientForwardingHeadersWithWhatTheServerSaw() throws Exception {
        MockHttpServletRequest request = request("GET", "/admin");
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        request.addHeader("X-Real-IP", "6.6.6.6");

        filter("http://127.0.0.1:" + port()).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Map<String, List<String>> headers = seenHeaders.get();
        assertEquals(List.of("203.0.113.7"), headers.get("X-forwarded-for"));
        assertEquals(List.of("https"), headers.get("X-forwarded-proto"));
        assertEquals(List.of("id.example.com"), headers.get("X-forwarded-host"));
        assertNull(headers.get("X-real-ip"), "client-supplied X-Real-IP must not reach the console");
    }

    @Test
    void forwardsRequestBodies() throws Exception {
        MockHttpServletRequest request = request("POST", "/admin/api/auth/login");
        request.setContentType("application/json");
        request.setContent("{\"emailOrUsername\":\"a\"}".getBytes(StandardCharsets.UTF_8));

        filter("http://127.0.0.1:" + port()).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("{\"emailOrUsername\":\"a\"}", seenBody.get());
    }

    @Test
    void rewritesRedirectsToTheInternalAddressAndKeepsEveryCookie() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter("http://127.0.0.1:" + port()).doFilter(request("GET", "/admin/tenants"), response, new MockFilterChain());

        assertEquals(307, response.getStatus());
        assertEquals("/admin/login?next=%2Ftenants", response.getHeader("Location"));
        assertEquals(List.of("a=1; Path=/", "b=2; Path=/"), response.getHeaders("Set-Cookie"));
    }

    @Test
    void answersServiceUnavailableWhileTheConsoleIsDown() throws Exception {
        int unused;
        try (ServerSocket socket = new ServerSocket(0)) {
            unused = socket.getLocalPort();
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter("http://127.0.0.1:" + unused).doFilter(request("GET", "/admin/login"), response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
    }

    @Test
    void leavesOtherPathsAndUnconfiguredServersAlone() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter("http://127.0.0.1:" + port()).doFilter(request("GET", "/administrator"), new MockHttpServletResponse(), chain);
        assertTrue(chain.getRequest() != null, "/administrator is not the console");

        MockFilterChain unconfigured = new MockFilterChain();
        filter("").doFilter(request("GET", "/admin/login"), new MockHttpServletResponse(), unconfigured);
        assertTrue(unconfigured.getRequest() != null, "without a console, /admin falls through to the app (404)");
        assertFalse(new ConsoleSettings("", "", 3001, "node").upstream().isPresent());
    }

    @Test
    void consoleDirectoryMeansALocalUpstream() {
        ConsoleSettings settings = new ConsoleSettings("", "/opt/secureone/console", 3101, "node");
        assertEquals("http://127.0.0.1:3101", settings.upstream().orElseThrow().toString());
        assertTrue(settings.launchConfigured());
        assertFalse(settings.serverScript().isPresent(), "no server.js there in the test environment");
    }
}
