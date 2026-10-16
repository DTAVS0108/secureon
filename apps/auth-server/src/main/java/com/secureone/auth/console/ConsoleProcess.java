package com.secureone.auth.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Runs the admin console (Node.js) as a child of the auth-server when {@code SECUREONE_CONSOLE_DIR} is
 * set, so the container has one entry point and one public port. The console listens on 127.0.0.1
 * only; {@link ConsoleProxyFilter} forwards /admin to it. It is restarted if it exits and stopped with
 * the auth-server. Its output goes to this log under the {@code console} logger.
 */
@Component
public class ConsoleProcess implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsoleProcess.class);
    private static final Logger consoleLog = LoggerFactory.getLogger("console");
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final ConsoleSettings settings;
    private final int serverPort;
    private final String publicBaseUrl;
    private final String environment;

    private volatile Process process;
    private volatile boolean running;

    public ConsoleProcess(
            ConsoleSettings settings,
            @Value("${server.port:9000}") int serverPort,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl,
            @Value("${secureone.environment:development}") String environment) {
        this.settings = settings;
        this.serverPort = serverPort;
        this.publicBaseUrl = publicBaseUrl;
        this.environment = environment;
    }

    @Override
    public void start() {
        if (!settings.launchConfigured()) {
            return;
        }
        Path script = settings.serverScript().orElse(null);
        if (script == null) {
            log.error("SECUREONE_CONSOLE_DIR={} has no server.js; the admin console is not available at /admin.",
                    settings.dir());
            return;
        }
        running = true;
        Thread supervisor = new Thread(() -> supervise(script), "console-supervisor");
        supervisor.setDaemon(true);
        supervisor.start();
    }

    private void supervise(Path script) {
        Duration backoff = Duration.ofSeconds(1);
        while (running) {
            long started = System.nanoTime();
            try {
                process = launch(script);
                log.info("Admin console started (pid {}) on 127.0.0.1:{}, served at /admin.",
                        process.pid(), settings.port());
                int exit = process.waitFor();
                if (!running) {
                    return;
                }
                log.warn("Admin console exited with code {}; restarting in {}s.", exit, backoff.toSeconds());
            } catch (IOException e) {
                log.error("Could not start the admin console ({}): {}", settings.nodeCommand(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // A console that ran for a while gets a fresh backoff; one that keeps crashing waits longer.
            if (Duration.ofNanos(System.nanoTime() - started).compareTo(MAX_BACKOFF) > 0) {
                backoff = Duration.ofSeconds(1);
            }
            sleep(backoff);
            backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
        }
    }

    private Process launch(Path script) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(settings.nodeCommand(), script.getFileName().toString())
                .directory(script.getParent().toFile())
                .redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("NODE_ENV", "production");
        env.put("NEXT_TELEMETRY_DISABLED", "1");
        env.put("HOSTNAME", "127.0.0.1");
        env.put("PORT", Integer.toString(settings.port()));
        // Keep idle connections open longer than the proxy's pool does (java.net.http: 30 s), so the
        // proxy never reuses a connection the console has already closed (Node's default is 5 s).
        env.put("KEEP_ALIVE_TIMEOUT", "65000");
        env.put("SECUREONE_AUTH_SERVER_INTERNAL_URL", "http://127.0.0.1:" + serverPort);
        env.put("SECUREONE_PUBLIC_BASE_URL", publicBaseUrl);
        env.put("SECUREONE_ENV", environment);
        // Only the /admin proxy can reach 127.0.0.1, so its X-Forwarded-For is the real client.
        env.put("SECUREONE_TRUST_PROXY_HEADERS", "true");
        Process started = builder.start();
        Thread pump = new Thread(() -> pumpOutput(started), "console-output");
        pump.setDaemon(true);
        pump.start();
        return started;
    }

    private static void pumpOutput(Process source) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consoleLog.info(line);
            }
        } catch (IOException ignored) {
            // Stream closes when the process exits.
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        Process current = process;
        if (current == null || !current.isAlive()) {
            return;
        }
        current.destroy();
        try {
            if (!current.waitFor(10, TimeUnit.SECONDS)) {
                current.destroyForcibly();
            }
        } catch (InterruptedException e) {
            current.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** True once the console process is up (it may still be loading). */
    public boolean isAlive() {
        Process current = process;
        return current != null && current.isAlive();
    }
}
