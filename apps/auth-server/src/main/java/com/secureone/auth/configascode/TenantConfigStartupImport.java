package com.secureone.auth.configascode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code SECUREONE_CONFIG_IMPORT}: applies tenant configuration files at every start-up (GitOps: mount
 * the files from a ConfigMap or volume). A file or a directory of *.yaml / *.yml / *.json, in name order.
 * Instances that start together take turns (advisory lock); the later ones find nothing to change.
 * A file with problems is logged and skipped; the server still starts.
 */
@Component
public class TenantConfigStartupImport {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigStartupImport.class);
    private static final String LOCK = "SELECT pg_advisory_lock(hashtext('secureone.config-import'))";
    private static final String UNLOCK = "SELECT pg_advisory_unlock(hashtext('secureone.config-import'))";

    private final TenantConfigService service;
    private final PlatformConfigService platform;
    private final JdbcTemplate jdbc;
    private final String location;

    public TenantConfigStartupImport(
            TenantConfigService service,
            PlatformConfigService platform,
            JdbcTemplate jdbc,
            @Value("${secureone.config-import:}") String location) {
        this.service = service;
        this.platform = platform;
        this.jdbc = jdbc;
        this.location = location;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE) // after the catalog repair has seeded the built-in roles
    public void importOnStartup() {
        if (location == null || location.isBlank()) {
            return;
        }
        List<Path> files = files(Path.of(location.trim()));
        if (files.isEmpty()) {
            log.warn("SECUREONE_CONFIG_IMPORT={} has no .yaml, .yml or .json files", location);
            return;
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement s = connection.createStatement()) {
                s.execute(LOCK);
            }
            try {
                files.forEach(this::importFile);
            } finally {
                try (Statement s = connection.createStatement()) {
                    s.execute(UNLOCK);
                }
            }
            return null;
        });
    }

    private void importFile(Path file) {
        String name = file.getFileName().toString();
        try {
            Object parsed = TenantConfigFormat.parseAny(Files.readString(file, StandardCharsets.UTF_8));
            TenantConfigService.Result result = parsed instanceof PlatformConfig config
                    ? platform.apply(config, "config-file:" + name)
                    : service.apply((TenantConfig) parsed, false, "config-file:" + name);
            result.errors().forEach(e -> log.error("Configuration file {}: {}", name, e));
            result.notes().forEach(n -> log.info("Configuration file {}: {}", name, n));
            for (TenantConfigService.CreatedSecret secret : result.secrets()) {
                log.warn("Configuration file {}: {} {} was created with a generated secret, which is not logged."
                        + " Name an environment variable for it in the file, or create a new one in the console.",
                        name, secret.kind(), secret.name());
            }
            if (result.errors().isEmpty()) {
                log.info("Configuration file {} (tenant {}): {}", name, result.tenant(),
                        result.changes().isEmpty() ? "already up to date" : result.changes().size() + " change(s) applied");
            }
        } catch (IOException | RuntimeException ex) {
            log.error("Configuration file {} was not applied: {}", name, ex.getMessage());
        }
    }

    private static List<Path> files(Path path) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            log.warn("SECUREONE_CONFIG_IMPORT={} does not exist", path);
            return List.of();
        }
        try (Stream<Path> list = Files.list(path)) {
            return list.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".json");
                    })
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            log.warn("Could not list {}: {}", path, ex.getMessage());
            return List.of();
        }
    }
}
