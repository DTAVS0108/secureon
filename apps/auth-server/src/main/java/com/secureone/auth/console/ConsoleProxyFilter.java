package com.secureone.auth.console;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Serves the admin console at {@value #PREFIX} by forwarding to the console server (see
 * {@link ConsoleSettings}), so the whole product is one domain and one port.
 *
 * <p>Runs before Spring Security: the console authenticates its operators itself (password + MFA
 * against this server's admin API) and calls the API with their session token, where the usual
 * checks apply. The client address is passed on in {@code X-Forwarded-For} as resolved by the servlet
 * container (trusted proxies only); whatever forwarding headers the client sent are dropped.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ConsoleProxyFilter extends OncePerRequestFilter {

    public static final String PREFIX = "/admin";

    /** Largest request body forwarded (user CSV imports are the biggest console uploads). */
    private static final int MAX_BODY_BYTES = 20 * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ConsoleProxyFilter.class);

    /** Connection-level or recomputed headers that must not be copied between the two hops. */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-connection", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "http2-settings", "content-length", "expect", "host");

    /** Client-supplied forwarding headers are replaced with what this server actually saw. */
    private static final Set<String> FORWARDING = Set.of(
            "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port",
            "x-forwarded-prefix", "x-real-ip");

    private static final Map<String, String> DEFAULT_SECURITY_HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Referrer-Policy", "strict-origin-when-cross-origin");

    private final ConsoleSettings settings;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ConsoleProxyFilter(ConsoleSettings settings) {
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.equals(PREFIX) || uri.startsWith(PREFIX + "/")) || settings.upstream().isEmpty();
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        URI upstream = settings.upstream().orElseThrow();
        if ("websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            tunnelUpgrade(request, response, upstream);
            return;
        }
        String query = request.getQueryString();
        URI target = URI.create(upstream + request.getRequestURI() + (query == null ? "" : "?" + query));

        HttpRequest.BodyPublisher body;
        try {
            body = body(request);
        } catch (BodyTooLargeException e) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        HttpRequest.Builder outbound = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMinutes(2))
                .method(request.getMethod(), body);
        for (String name : Collections.list(request.getHeaderNames())) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower) || FORWARDING.contains(lower)) {
                continue;
            }
            for (String value : Collections.list(request.getHeaders(name))) {
                outbound.header(name, value);
            }
        }
        String host = request.getHeader("Host");
        if (host != null) {
            // Next.js builds redirects and checks Server Action origins against the public host.
            outbound.header("X-Forwarded-Host", host);
            try {
                outbound.header("Host", host);
            } catch (IllegalArgumentException restricted) {
                // jdk.httpclient.allowRestrictedHeaders was not set in time (see AuthServerApplication):
                // X-Forwarded-Host still carries it, and Location headers are rewritten below.
            }
        }
        outbound.header("X-Forwarded-For", request.getRemoteAddr());
        outbound.header("X-Forwarded-Proto", request.getScheme());
        outbound.header("X-Forwarded-Port", Integer.toString(request.getServerPort()));

        HttpResponse<InputStream> upstreamResponse;
        try {
            upstreamResponse = client.send(outbound.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            unavailable(response);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            unavailable(response);
            return;
        } catch (IOException e) {
            log.warn("Admin console request {} {} failed: {}", request.getMethod(), request.getRequestURI(), e.toString());
            unavailable(response);
            return;
        }

        response.setStatus(upstreamResponse.statusCode());
        upstreamResponse.headers().map().forEach((name, values) -> copyResponseHeader(response, upstream, name, values));
        DEFAULT_SECURITY_HEADERS.forEach((name, value) -> {
            if (!response.containsHeader(name)) {
                response.setHeader(name, value);
            }
        });
        if (request.isSecure() && !response.containsHeader("Strict-Transport-Security")) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        upstreamResponse.headers().firstValueAsLong("content-length").ifPresent(response::setContentLengthLong);

        try (InputStream in = upstreamResponse.body()) {
            OutputStream out = response.getOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                // Pages stream in chunks (React Server Components); pass each chunk on as it arrives.
                if (in.available() == 0) {
                    out.flush();
                }
            }
            out.flush();
        }
    }

    /**
     * Relays a WebSocket handshake to the console over a plain socket and, if the console accepts it,
     * hands the connection to {@link ConsoleUpgradeTunnel}. Only live reload in development uses this.
     */
    private void tunnelUpgrade(HttpServletRequest request, HttpServletResponse response, URI upstream)
            throws IOException, ServletException {
        int port = upstream.getPort() > 0 ? upstream.getPort() : 80;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(upstream.getHost(), port), 5000);
            String query = request.getQueryString();
            StringBuilder handshake = new StringBuilder()
                    .append(request.getMethod()).append(' ')
                    .append(request.getRequestURI()).append(query == null ? "" : "?" + query)
                    .append(" HTTP/1.1\r\n");
            for (String name : Collections.list(request.getHeaderNames())) {
                if (FORWARDING.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                for (String value : Collections.list(request.getHeaders(name))) {
                    handshake.append(name).append(": ").append(value).append("\r\n");
                }
            }
            handshake.append("X-Forwarded-For: ").append(request.getRemoteAddr()).append("\r\n")
                    .append("X-Forwarded-Proto: ").append(request.getScheme()).append("\r\n\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(handshake.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            InputStream in = new BufferedInputStream(socket.getInputStream());
            List<String> lines = readHead(in);
            String[] status = lines.isEmpty() ? new String[0] : lines.get(0).split(" ", 3);
            int code = status.length > 1 ? Integer.parseInt(status[1]) : 502;
            response.setStatus(code);
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    response.addHeader(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
            if (code != HttpServletResponse.SC_SWITCHING_PROTOCOLS) {
                socket.close();
                return;
            }
            ConsoleUpgradeTunnel tunnel = request.upgrade(ConsoleUpgradeTunnel.class);
            tunnel.attach(socket, in);
        } catch (IOException | RuntimeException e) {
            socket.close();
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
            }
        }
    }

    /** Status line and headers of an HTTP/1.1 response, up to the blank line (16 KB at most). */
    private static List<String> readHead(InputStream in) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
        int total = 0;
        int b;
        while ((b = in.read()) != -1 && total++ < 16 * 1024) {
            if (b == '\n') {
                String text = line.toString(StandardCharsets.ISO_8859_1).stripTrailing();
                if (text.isEmpty()) {
                    return lines;
                }
                lines.add(text);
                line.reset();
            } else {
                line.write(b);
            }
        }
        throw new IOException("Incomplete upgrade response from the console");
    }

    /**
     * Console request bodies are small (forms, JSON, CSV imports), so they are read here on the request
     * thread rather than streamed from the HTTP client's own threads.
     */
    private static HttpRequest.BodyPublisher body(HttpServletRequest request) throws IOException {
        long length = request.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        if (length <= 0 && !chunked) {
            return HttpRequest.BodyPublishers.noBody();
        }
        if (length > MAX_BODY_BYTES) {
            throw new BodyTooLargeException();
        }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new BodyTooLargeException();
        }
        return HttpRequest.BodyPublishers.ofByteArray(bytes);
    }

    private static final class BodyTooLargeException extends IOException {
        BodyTooLargeException() {
            super("Request body larger than " + MAX_BODY_BYTES + " bytes");
        }
    }

    private static void copyResponseHeader(HttpServletResponse response, URI upstream, String name, List<String> values) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (HOP_BY_HOP.contains(lower) || lower.startsWith(":")) {
            return;
        }
        for (String value : values) {
            if ("location".equals(lower)) {
                // Never send the browser to the internal console address.
                String origin = upstream.toString();
                if (value.startsWith(origin)) {
                    value = value.substring(origin.length());
                    if (value.isEmpty()) {
                        value = "/";
                    }
                }
            }
            response.addHeader(name, value);
        }
    }

    private static void unavailable(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "5");
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        response.getOutputStream().write(("<!doctype html><meta http-equiv=refresh content=5>"
                        + "<title>SecureOne</title><p style=\"font-family:system-ui;margin:3rem\">"
                        + "The admin console is starting. This page reloads in a few seconds.</p>")
                .getBytes(StandardCharsets.UTF_8));
    }
}
