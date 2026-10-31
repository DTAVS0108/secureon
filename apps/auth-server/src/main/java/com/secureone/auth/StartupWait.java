package com.secureone.auth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Before the server starts: waits until PostgreSQL and Redis accept connections, so SecureOne can be started
 * together with them (Compose, Kubernetes, a reboot) instead of failing and restarting until they are up.
 * {@code SECUREONE_STARTUP_WAIT} is the limit in seconds (default 120, 0 turns it off); after it, start-up goes on
 * and reports the real connection error.
 */
final class StartupWait {

    private static final Pattern JDBC_HOST = Pattern.compile("^jdbc:postgresql://\\[?([^\\]/:,?]+)\\]?(?::(\\d+))?");
    private static final long RETRY_MILLIS = 2000;

    private StartupWait() {}

    static void forDependencies() {
        long limitSeconds = parseSeconds(setting("SECUREONE_STARTUP_WAIT", "120"));
        if (limitSeconds <= 0) {
            return;
        }
        long deadline = System.currentTimeMillis() + limitSeconds * 1000;
        Endpoint database = database(setting("SECUREONE_DB_URL",
                "jdbc:postgresql://localhost:5432/secureone?currentSchema=platform"));
        Endpoint redis = redis(setting("SECUREONE_REDIS_URL", "redis://localhost:6379"));
        for (Endpoint endpoint : new Endpoint[] {database, redis}) {
            if (endpoint != null && !waitFor(endpoint, deadline)) {
                System.err.printf("SecureOne: %s at %s:%d is still not reachable after %d seconds; starting anyway.%n",
                        endpoint.name(), endpoint.host(), endpoint.port(), limitSeconds);
            }
        }
    }

    record Endpoint(String name, String host, int port) {}

    static Endpoint database(String url) {
        Matcher m = JDBC_HOST.matcher(url == null ? "" : url.trim());
        if (!m.find()) {
            return null;
        }
        return new Endpoint("PostgreSQL", m.group(1), m.group(2) == null ? 5432 : Integer.parseInt(m.group(2)));
    }

    static Endpoint redis(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            if (uri.getHost() == null) {
                return null;
            }
            return new Endpoint("Redis", uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 6379);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean waitFor(Endpoint endpoint, long deadline) {
        boolean announced = false;
        while (true) {
            if (reachable(endpoint)) {
                if (announced) {
                    System.err.printf("SecureOne: %s at %s:%d is reachable.%n", endpoint.name(), endpoint.host(), endpoint.port());
                }
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            if (!announced) {
                System.err.printf("SecureOne: waiting for %s at %s:%d ...%n", endpoint.name(), endpoint.host(), endpoint.port());
                announced = true;
            }
            try {
                Thread.sleep(RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static boolean reachable(Endpoint endpoint) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** An environment variable, or the file of the same name in SECUREONE_SECRETS_DIR (as the server reads them). */
    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        String dir = System.getenv().getOrDefault("SECUREONE_SECRETS_DIR", "/run/secrets");
        try {
            Path file = Path.of(dir, name);
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException | RuntimeException ignored) {
            // Unreadable: use the default, as the server would.
        }
        return fallback;
    }

    private static long parseSeconds(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 120;
        }
    }
}
