package com.secureone.auth.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lets {@code SECUREONE_DB_URL} be the {@code postgres://user:password@host:port/database} URL that hosting
 * providers hand out (Render, Railway, DigitalOcean, Heroku-style {@code DATABASE_URL}). It becomes the JDBC URL
 * the server uses, with {@code currentSchema=platform} added; the user and password in it are used unless
 * {@code SECUREONE_DB_USERNAME} / {@code SECUREONE_DB_PASSWORD} are set. A {@code jdbc:} URL is left as it is.
 */
public class SecureOneDatabaseUrl implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "secureoneDatabaseUrl";

    /** A provider URL turned into what the server reads. */
    public record Converted(String jdbcUrl, String username, String password) {}

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Converted converted = convert(environment.getProperty("SECUREONE_DB_URL"));
        if (converted == null) {
            return;
        }
        Map<String, Object> values = new HashMap<>();
        values.put("SECUREONE_DB_URL", converted.jdbcUrl());
        if (converted.username() != null && isBlank(environment.getProperty("SECUREONE_DB_USERNAME"))) {
            values.put("SECUREONE_DB_USERNAME", converted.username());
        }
        if (converted.password() != null && isBlank(environment.getProperty("SECUREONE_DB_PASSWORD"))) {
            values.put("SECUREONE_DB_PASSWORD", converted.password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, values));
    }

    /** The JDBC form of a {@code postgres://} or {@code postgresql://} URL, or null for anything else. */
    public static Converted convert(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        String lower = value.toLowerCase();
        if (!lower.startsWith("postgres://") && !lower.startsWith("postgresql://")) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SECUREONE_DB_URL is not a valid postgres:// URL.");
        }
        if (uri.getHost() == null) {
            throw new IllegalStateException("SECUREONE_DB_URL has no host name.");
        }
        String database = uri.getRawPath() == null || uri.getRawPath().length() <= 1 ? "secureone" : uri.getRawPath().substring(1);
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append('/').append(database);
        String query = uri.getRawQuery();
        boolean hasSchema = false;
        StringBuilder params = new StringBuilder();
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                if (param.isBlank()) {
                    continue;
                }
                hasSchema |= param.toLowerCase().startsWith("currentschema=");
                params.append(params.isEmpty() ? "" : "&").append(param);
            }
        }
        if (!hasSchema) {
            params.append(params.isEmpty() ? "" : "&").append("currentSchema=platform");
        }
        jdbc.append('?').append(params);

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
        }
        return new Converted(jdbc.toString(), username, password);
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** After the configuration files and the secrets folder are loaded, so a URL kept as a secret file is seen. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
