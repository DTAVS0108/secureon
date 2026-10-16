package com.secureone.auth.upgrade;

import com.secureone.auth.application.ApplicationSchemaMigrations;
import com.secureone.auth.application.ApplicationSchemaUpgrader;
import com.secureone.auth.oauth.ClientSecretUpgrade;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the database to this release before anything else uses it, or refuses to start.
 *
 * <ul>
 *   <li>{@code auto} (default): apply the missing platform migrations and upgrade every application schema.
 *   <li>{@code only}: the same, then exit; for an upgrade job that runs before the new instances start.
 *   <li>{@code validate}: change nothing; refuse to start while anything is missing (the job did not run).
 * </ul>
 *
 * In every mode a database that a newer release has already upgraded is refused: older code cannot know
 * what the newer migrations changed.
 */
public final class DatabaseUpgrade {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUpgrade.class);

    public enum Mode {
        AUTO,
        ONLY,
        VALIDATE;

        public static Mode parse(String value) {
            if (value == null || value.isBlank()) {
                return AUTO;
            }
            try {
                return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "SECUREONE_DB_MIGRATE must be auto, only or validate (was '" + value + "')");
            }
        }
    }

    private final Flyway flyway;
    private final Mode mode;
    private final ApplicationSchemaUpgrader applicationSchemas;
    private final DatabaseVersionState state;
    private final IntSupplier dataUpgrades;

    public DatabaseUpgrade(Flyway flyway, Mode mode, DatabaseVersionState state) {
        this(flyway, mode, new ApplicationSchemaUpgrader(flyway.getConfiguration().getDataSource()), state,
                new ClientSecretUpgrade(flyway.getConfiguration().getDataSource())::run);
    }

    DatabaseUpgrade(Flyway flyway, Mode mode, ApplicationSchemaUpgrader applicationSchemas, DatabaseVersionState state) {
        this(flyway, mode, applicationSchemas, state, () -> 0);
    }

    DatabaseUpgrade(Flyway flyway, Mode mode, ApplicationSchemaUpgrader applicationSchemas, DatabaseVersionState state,
            IntSupplier dataUpgrades) {
        this.flyway = flyway;
        this.mode = mode;
        this.applicationSchemas = applicationSchemas;
        this.state = state;
        this.dataUpgrades = dataUpgrades;
    }

    public void run() {
        MigrationInfoService info = flyway.info();
        String known = latestKnown(info);
        refuseNewerPlatform(info, known);
        ApplicationSchemaUpgrader.Status apps = applicationSchemas.status();
        refuseNewerApplications(apps);

        if (mode == Mode.VALIDATE) {
            MigrationInfo[] pending = info.pending();
            if (pending.length > 0 || !apps.behind().isEmpty()) {
                throw new IllegalStateException(String.format(
                        "The database is older than this release (%d platform migration(s) and %d application"
                                + " schema(s) pending) and SECUREONE_DB_MIGRATE=validate does not change it. Run the"
                                + " upgrade job (the same image with SECUREONE_DB_MIGRATE=only) first.",
                        pending.length, apps.behind().size()));
            }
        } else {
            int pending = info.pending().length;
            if (pending > 0) {
                log.info("Database upgrade: applying {} platform migration(s) up to V{}", pending, known);
            }
            flyway.migrate();
            int upgraded = applicationSchemas.upgrade();
            // Data that older releases stored in a weaker form (plain-text client secrets).
            dataUpgrades.getAsInt();
            if (pending > 0 || upgraded > 0) {
                log.info("Database upgrade done: {} platform migration(s), {} application schema(s) upgraded", pending, upgraded);
            }
        }

        MigrationInfo current = flyway.info().current();
        String platformVersion = current != null && current.getVersion() != null ? current.getVersion().getVersion() : "none";
        state.record(platformVersion, ApplicationSchemaMigrations.latest().version(), apps.count(), mode.name().toLowerCase(Locale.ROOT));
        log.info("Database schema: platform V{}, application schemas V{} ({} schema(s)), mode {}",
                platformVersion, ApplicationSchemaMigrations.latest().version(), apps.count(), mode.name().toLowerCase(Locale.ROOT));
    }

    private void refuseNewerPlatform(MigrationInfoService info, String known) {
        List<MigrationInfo> future = Arrays.stream(info.applied())
                .filter(m -> m.getState().name().startsWith("FUTURE"))
                .toList();
        if (future.isEmpty()) {
            return;
        }
        String versions = future.stream()
                .map(m -> "V" + m.getVersion().getVersion())
                .collect(Collectors.joining(", "));
        throw new IllegalStateException(String.format(
                "The database was upgraded by a newer SecureOne release (it has %s; this release knows up to V%s)."
                        + " Running an older release against it could damage data, so this instance will not start."
                        + " Start the newer release again, or restore the backup taken before the upgrade and then"
                        + " start this release.",
                versions, known));
    }

    private static void refuseNewerApplications(ApplicationSchemaUpgrader.Status apps) {
        if (apps.ahead().isEmpty()) {
            return;
        }
        throw new IllegalStateException(String.format(
                "%d application schema(s) were upgraded by a newer SecureOne release (for example %s at V%d; this"
                        + " release knows up to V%d). Start the newer release again, or restore the backup taken"
                        + " before the upgrade.",
                apps.ahead().size(), apps.ahead().getFirst().schemaName(), apps.ahead().getFirst().version(),
                ApplicationSchemaMigrations.latest().version()));
    }

    private static String latestKnown(MigrationInfoService info) {
        return Arrays.stream(info.all())
                .filter(m -> m.getVersion() != null && !m.getState().name().startsWith("FUTURE"))
                .map(MigrationInfo::getVersion)
                .max(Comparable::compareTo)
                .map(v -> v.getVersion())
                .orElse("0");
    }
}
