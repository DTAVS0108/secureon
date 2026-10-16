package com.secureone.auth.oauth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hashes OAuth client secrets that older releases stored in plain text. Runs with the database upgrade
 * (Flyway's connection, before the server uses the database); idempotent, and each row is changed only if
 * it still holds the plain value, so instances that start together do not hash a hash.
 */
public final class ClientSecretUpgrade {

    private static final Logger log = LoggerFactory.getLogger(ClientSecretUpgrade.class);

    private final DataSource dataSource;

    public ClientSecretUpgrade(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns the number of secrets hashed. */
    public int run() {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet table = s.executeQuery("SELECT to_regclass('platform.oauth_client') IS NOT NULL")) {
                if (!table.next() || !table.getBoolean(1)) {
                    return 0;
                }
            }
            s.execute("SELECT set_config('app.rls_bypass', 'on', false)");
            Map<UUID, String> plain = new LinkedHashMap<>();
            try (ResultSet rs = s.executeQuery(
                    "SELECT id, client_secret FROM platform.oauth_client WHERE client_secret IS NOT NULL"
                            + " AND client_secret <> '' AND client_secret NOT LIKE '{bcrypt}%' AND client_secret NOT LIKE '$2%'")) {
                while (rs.next()) {
                    plain.put(rs.getObject(1, UUID.class), rs.getString(2));
                }
            }
            int hashed = 0;
            try (PreparedStatement update = c.prepareStatement(
                    "UPDATE platform.oauth_client SET client_secret = ? WHERE id = ? AND client_secret = ?")) {
                for (Map.Entry<UUID, String> row : plain.entrySet()) {
                    update.setString(1, ClientSecrets.hash(row.getValue()));
                    update.setObject(2, row.getKey());
                    update.setString(3, row.getValue());
                    hashed += update.executeUpdate();
                }
            }
            if (hashed > 0) {
                log.info("Hashed {} OAuth client secret(s) that were stored in plain text", hashed);
            }
            return hashed;
        } catch (SQLException ex) {
            throw new IllegalStateException("Hashing stored OAuth client secrets failed: " + ex.getMessage(), ex);
        }
    }
}
