package com.secureone.auth.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings every per-application schema to {@link ApplicationSchemaMigrations#latest()}. Runs right after
 * the platform migrations, on Flyway's connection (the schema owner), before anything else touches the
 * database. A Postgres advisory lock keeps two instances that start together from upgrading the same
 * schema twice; each schema is upgraded in its own transaction and its recorded version moves with it.
 */
public final class ApplicationSchemaUpgrader {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSchemaUpgrader.class);
    private static final String LOCK = "SELECT pg_advisory_lock(hashtext('secureone.application-schema-upgrade'))";
    private static final String UNLOCK = "SELECT pg_advisory_unlock(hashtext('secureone.application-schema-upgrade'))";

    /** One active application schema and the migration version it has. */
    public record SchemaVersion(String schemaName, int version) {}

    /** What the database holds, relative to this release. */
    public record Status(int count, List<SchemaVersion> behind, List<SchemaVersion> ahead) {}

    private final DataSource dataSource;

    public ApplicationSchemaUpgrader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Status status() {
        try (Connection c = dataSource.getConnection()) {
            return status(read(c));
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read the application schema versions: " + ex.getMessage(), ex);
        }
    }

    /** Applies the missing migrations to every active application schema; returns the number upgraded. */
    public int upgrade() {
        int latest = ApplicationSchemaMigrations.latest().version();
        try (Connection c = dataSource.getConnection()) {
            try (Statement s = c.createStatement()) {
                s.execute(LOCK);
            }
            try {
                int upgraded = 0;
                for (SchemaVersion schema : read(c)) {
                    if (schema.version() < latest && upgrade(c, schema)) {
                        upgraded++;
                    }
                }
                return upgraded;
            } finally {
                try (Statement s = c.createStatement()) {
                    s.execute(UNLOCK);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Upgrading the application schemas failed: " + ex.getMessage(), ex);
        }
    }

    private boolean upgrade(Connection c, SchemaVersion schema) throws SQLException {
        if (!schemaExists(c, schema.schemaName())) {
            log.warn("Application schema {} is registered but missing; skipped", schema.schemaName());
            return false;
        }
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        ApplicationSchemaMigrations.Migration current = null;
        try (Statement s = c.createStatement()) {
            s.execute("SET LOCAL search_path TO " + quoteIdent(schema.schemaName()));
            for (ApplicationSchemaMigrations.Migration migration : ApplicationSchemaMigrations.ALL) {
                if (migration.version() <= schema.version()) {
                    continue;
                }
                current = migration;
                for (String statement : ApplicationSchemaMigrations.statements(migration)) {
                    s.execute(statement);
                }
            }
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE platform.application_schema SET flyway_version = ?, updated_at = now() WHERE schema_name = ?")) {
                p.setString(1, ApplicationSchemaMigrations.latest().name());
                p.setString(2, schema.schemaName());
                p.executeUpdate();
            }
            c.commit();
            log.info("Upgraded application schema {} from V{} to {}",
                    schema.schemaName(), schema.version(), ApplicationSchemaMigrations.latest().name());
            return true;
        } catch (SQLException | RuntimeException ex) {
            c.rollback();
            throw new IllegalStateException("Upgrading application schema " + schema.schemaName() + " with "
                    + (current != null ? current.name() : "?") + " failed (rolled back): " + ex.getMessage(), ex);
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    private static List<SchemaVersion> read(Connection c) throws SQLException {
        List<SchemaVersion> out = new ArrayList<>();
        try (Statement s = c.createStatement();
                ResultSet table = s.executeQuery("SELECT to_regclass('platform.application_schema') IS NOT NULL")) {
            // A new installation: the platform migrations have not created the registry yet.
            if (!table.next() || !table.getBoolean(1)) {
                return out;
            }
        }
        try (Statement s = c.createStatement()) {
            // The migration user owns these tables; the RLS policies (V61) still apply to it when forced.
            s.execute("SELECT set_config('app.rls_bypass', 'on', false)");
            try (ResultSet rs = s.executeQuery(
                    "SELECT schema_name, flyway_version FROM platform.application_schema"
                            + " WHERE upper(status) = 'ACTIVE' ORDER BY schema_name")) {
                while (rs.next()) {
                    out.add(new SchemaVersion(rs.getString(1), ApplicationSchemaMigrations.parseVersion(rs.getString(2))));
                }
            }
        }
        return out;
    }

    private static Status status(List<SchemaVersion> schemas) {
        int latest = ApplicationSchemaMigrations.latest().version();
        return new Status(
                schemas.size(),
                schemas.stream().filter(s -> s.version() < latest).toList(),
                schemas.stream().filter(s -> s.version() > latest).toList());
    }

    private static boolean schemaExists(Connection c, String schemaName) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            p.setString(1, schemaName);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
