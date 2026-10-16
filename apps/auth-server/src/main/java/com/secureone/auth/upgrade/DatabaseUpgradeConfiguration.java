package com.secureone.auth.upgrade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Replaces Spring Boot's plain {@code flyway.migrate()} with {@link DatabaseUpgrade}. */
@Configuration(proxyBeanMethods = false)
public class DatabaseUpgradeConfiguration {

    @Bean
    DatabaseVersionState databaseVersionState() {
        return new DatabaseVersionState();
    }

    @Bean
    FlywayMigrationStrategy secureOneDatabaseUpgrade(
            @Value("${secureone.database.migrate:auto}") String mode, DatabaseVersionState state) {
        DatabaseUpgrade.Mode parsed = DatabaseUpgrade.Mode.parse(mode);
        return flyway -> new DatabaseUpgrade(flyway, parsed, state).run();
    }

    /** {@code database} section of /actuator/info on the management port. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(InfoContributor.class)
    static class Info {
        @Bean
        InfoContributor databaseInfoContributor(DatabaseVersionState state) {
            return builder -> builder.withDetail("database", state.details());
        }
    }
}
