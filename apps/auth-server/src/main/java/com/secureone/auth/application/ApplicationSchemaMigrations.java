package com.secureone.auth.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/**
 * The migrations of a per-application schema, in order. New schemas get all of them when they are
 * provisioned; existing schemas get the ones they miss at start-up ({@link ApplicationSchemaUpgrader}).
 * To change the per-application tables, add a file under {@code db/migration/application-schema} and
 * list it here; never edit a released file.
 */
public final class ApplicationSchemaMigrations {

    public record Migration(int version, String name) {
        String resource() {
            return "db/migration/application-schema/" + name + ".sql";
        }
    }

    public static final List<Migration> ALL = List.of(
            new Migration(1, "V1__app_core"),
            new Migration(2, "V2__user_application_created_at"),
            new Migration(3, "V3__two_step_mode"));

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

    private ApplicationSchemaMigrations() {}

    public static Migration latest() {
        return ALL.getLast();
    }

    /**
     * The version recorded in {@code application_schema.flyway_version} ("V2__..."). Schemas from before
     * versions were tracked carry an older name or none; they have at least V1, and V2 is idempotent.
     */
    public static int parseVersion(String recorded) {
        if (recorded == null || recorded.isBlank()) {
            return 1;
        }
        Matcher m = VERSION.matcher(recorded.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** The SQL statements of one migration (line comments removed, split on semicolons). */
    public static List<String> statements(Migration migration) {
        String ddl;
        try {
            ddl = new ClassPathResource(migration.resource()).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing application schema migration " + migration.resource(), ex);
        }
        List<String> out = new ArrayList<>();
        for (String statement : ddl.replaceAll("(?m)^--.*$", "").split(";")) {
            if (!statement.isBlank()) {
                out.add(statement.trim());
            }
        }
        return out;
    }
}
