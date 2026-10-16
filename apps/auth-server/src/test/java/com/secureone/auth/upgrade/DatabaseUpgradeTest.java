package com.secureone.auth.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.ApplicationSchemaMigrations;
import com.secureone.auth.application.ApplicationSchemaUpgrader;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class DatabaseUpgradeTest {

    private static MigrationInfo migration(String version, MigrationState state) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(info.getState()).thenReturn(state);
        return info;
    }

    private static Flyway flyway(MigrationInfo[] applied, MigrationInfo[] pending, MigrationInfo[] all) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(info.applied()).thenReturn(applied);
        when(info.pending()).thenReturn(pending);
        when(info.all()).thenReturn(all);
        when(info.current()).thenReturn(applied.length > 0 ? applied[applied.length - 1] : null);
        when(flyway.info()).thenReturn(info);
        return flyway;
    }

    private static ApplicationSchemaUpgrader apps(ApplicationSchemaUpgrader.Status status) {
        ApplicationSchemaUpgrader upgrader = mock(ApplicationSchemaUpgrader.class);
        when(upgrader.status()).thenReturn(status);
        return upgrader;
    }

    private static final ApplicationSchemaUpgrader.Status NO_APPS = new ApplicationSchemaUpgrader.Status(0, List.of(), List.of());

    @Test
    void aDatabaseUpgradedByANewerReleaseIsRefusedInEveryMode() {
        MigrationInfo v67 = migration("67", MigrationState.SUCCESS);
        MigrationInfo v68 = migration("68", MigrationState.FUTURE_SUCCESS);
        for (DatabaseUpgrade.Mode mode : DatabaseUpgrade.Mode.values()) {
            Flyway flyway = flyway(new MigrationInfo[] {v67, v68}, new MigrationInfo[0], new MigrationInfo[] {v67, v68});
            var error = assertThrows(IllegalStateException.class,
                    () -> new DatabaseUpgrade(flyway, mode, apps(NO_APPS), new DatabaseVersionState()).run());
            assertTrue(error.getMessage().contains("newer SecureOne release (it has V68; this release knows up to V67)"), error.getMessage());
            verify(flyway, never()).migrate();
        }
    }

    @Test
    void applicationSchemasAheadOfThisReleaseAreRefused() {
        MigrationInfo v67 = migration("67", MigrationState.SUCCESS);
        int next = ApplicationSchemaMigrations.latest().version() + 1;
        var ahead = new ApplicationSchemaUpgrader.Status(1, List.of(),
                List.of(new ApplicationSchemaUpgrader.SchemaVersion("app_shop", next)));
        Flyway flyway = flyway(new MigrationInfo[] {v67}, new MigrationInfo[0], new MigrationInfo[] {v67});
        var error = assertThrows(IllegalStateException.class,
                () -> new DatabaseUpgrade(flyway, DatabaseUpgrade.Mode.AUTO, apps(ahead), new DatabaseVersionState()).run());
        assertTrue(error.getMessage().contains("app_shop at V" + next), error.getMessage());
    }

    @Test
    void autoMigratesPlatformAndApplicationSchemas() {
        MigrationInfo v67 = migration("67", MigrationState.SUCCESS);
        MigrationInfo v68 = migration("68", MigrationState.PENDING);
        Flyway flyway = flyway(new MigrationInfo[] {v67}, new MigrationInfo[] {v68}, new MigrationInfo[] {v67, v68});
        ApplicationSchemaUpgrader upgrader = apps(NO_APPS);
        DatabaseVersionState state = new DatabaseVersionState();
        new DatabaseUpgrade(flyway, DatabaseUpgrade.Mode.AUTO, upgrader, state).run();
        verify(flyway).migrate();
        verify(upgrader).upgrade();
        assertEquals("auto", state.details().get("migrateMode"));
    }

    @Test
    void validateRefusesPendingWorkAndChangesNothing() {
        MigrationInfo v67 = migration("67", MigrationState.SUCCESS);
        MigrationInfo v68 = migration("68", MigrationState.PENDING);
        Flyway flyway = flyway(new MigrationInfo[] {v67}, new MigrationInfo[] {v68}, new MigrationInfo[] {v67, v68});
        ApplicationSchemaUpgrader upgrader = apps(NO_APPS);
        var error = assertThrows(IllegalStateException.class,
                () -> new DatabaseUpgrade(flyway, DatabaseUpgrade.Mode.VALIDATE, upgrader, new DatabaseVersionState()).run());
        assertTrue(error.getMessage().contains("SECUREONE_DB_MIGRATE=only"), error.getMessage());
        verify(flyway, never()).migrate();
        verify(upgrader, never()).upgrade();
    }

    @Test
    void validatePassesWhenUpToDate() {
        MigrationInfo v67 = migration("67", MigrationState.SUCCESS);
        Flyway flyway = flyway(new MigrationInfo[] {v67}, new MigrationInfo[0], new MigrationInfo[] {v67});
        DatabaseVersionState state = new DatabaseVersionState();
        new DatabaseUpgrade(flyway, DatabaseUpgrade.Mode.VALIDATE, apps(NO_APPS), state).run();
        assertEquals("67", state.details().get("platformSchemaVersion"));
    }

    @Test
    void storedDataIsUpgradedWithTheSchemaButNotInValidateMode() {
        MigrationInfo v67 = migration("67", MigrationState.SUCCESS);
        int[] runs = {0};
        Flyway flyway = flyway(new MigrationInfo[] {v67}, new MigrationInfo[0], new MigrationInfo[] {v67});
        new DatabaseUpgrade(flyway, DatabaseUpgrade.Mode.AUTO, apps(NO_APPS), new DatabaseVersionState(), () -> ++runs[0]).run();
        assertEquals(1, runs[0], "plain-text client secrets hashed in auto mode");
        new DatabaseUpgrade(flyway, DatabaseUpgrade.Mode.VALIDATE, apps(NO_APPS), new DatabaseVersionState(), () -> ++runs[0]).run();
        assertEquals(1, runs[0], "validate changes nothing");
    }

    @Test
    void modeNamesAreChecked() {
        assertEquals(DatabaseUpgrade.Mode.AUTO, DatabaseUpgrade.Mode.parse(null));
        assertEquals(DatabaseUpgrade.Mode.ONLY, DatabaseUpgrade.Mode.parse(" Only "));
        assertThrows(IllegalStateException.class, () -> DatabaseUpgrade.Mode.parse("yes"));
    }

    @Test
    void recordedApplicationSchemaVersionsAreParsed() {
        assertEquals(1, ApplicationSchemaMigrations.parseVersion(null));
        assertEquals(1, ApplicationSchemaMigrations.parseVersion("V1__app_core"));
        assertEquals(2, ApplicationSchemaMigrations.parseVersion("V2__user_application_created_at"));
        assertEquals(12, ApplicationSchemaMigrations.parseVersion("V12__something"));
    }
}
