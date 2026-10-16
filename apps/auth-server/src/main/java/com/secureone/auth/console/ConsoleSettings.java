package com.secureone.auth.console;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where the admin console is served from. The auth-server forwards {@value ConsoleProxyFilter#PREFIX}
 * to it, so users, apps and operators see one domain:
 *
 * <ul>
 *   <li>{@code SECUREONE_CONSOLE_DIR}: the built console (Next.js standalone {@code server.js}). The
 *       auth-server runs it as a child process on 127.0.0.1 (the single container image sets this).
 *   <li>{@code SECUREONE_CONSOLE_UPSTREAM_URL}: a console already running elsewhere, e.g. {@code next dev}
 *       on http://localhost:3001 during development.
 *   <li>Neither: /admin is not served here (run the console on its own host).
 * </ul>
 */
@Component
public class ConsoleSettings {

    private static final String SERVER_SCRIPT = "server.js";

    private final String upstreamUrl;
    private final String dir;
    private final int port;
    private final String nodeCommand;

    public ConsoleSettings(
            @Value("${secureone.console.upstream-url:}") String upstreamUrl,
            @Value("${secureone.console.dir:}") String dir,
            @Value("${secureone.console.port:3001}") int port,
            @Value("${secureone.console.node-command:node}") String nodeCommand) {
        this.upstreamUrl = trim(upstreamUrl);
        this.dir = trim(dir);
        this.port = port;
        this.nodeCommand = trim(nodeCommand).isEmpty() ? "node" : trim(nodeCommand);
    }

    /** The console process to start, when this auth-server runs it (single image). */
    public Optional<Path> serverScript() {
        if (dir.isEmpty()) {
            return Optional.empty();
        }
        Path script = Path.of(dir).resolve(SERVER_SCRIPT);
        return Files.isRegularFile(script) ? Optional.of(script) : Optional.empty();
    }

    public boolean launchConfigured() {
        return !dir.isEmpty();
    }

    /** Base URL /admin requests are forwarded to; empty when the console is not served here. */
    public Optional<URI> upstream() {
        if (!upstreamUrl.isEmpty()) {
            return Optional.of(URI.create(upstreamUrl.replaceAll("/+$", "")));
        }
        if (!dir.isEmpty()) {
            return Optional.of(URI.create("http://127.0.0.1:" + port));
        }
        return Optional.empty();
    }

    public int port() {
        return port;
    }

    public String nodeCommand() {
        return nodeCommand;
    }

    public String dir() {
        return dir;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
