package com.secureone.auth.upgrade;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * {@code SECUREONE_DB_MIGRATE=only}: upgrade the database and exit, without Redis, the web server or the
 * console. Run it as a job before rolling out a new release (the Helm chart's pre-upgrade hook does).
 * Deliberately not a {@code @Component}, so the main application's component scan ignores it.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(DatabaseUpgradeConfiguration.class)
public class MigrateOnly {

    public static boolean requested() {
        String mode = System.getenv("SECUREONE_DB_MIGRATE");
        if (mode == null) {
            mode = System.getProperty("secureone.database.migrate");
        }
        return mode != null && "only".equalsIgnoreCase(mode.trim());
    }

    public static int run(String[] args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MigrateOnly.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .run(args)) {
            return SpringApplication.exit(context);
        }
    }
}
