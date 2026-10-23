package com.secureone.auth.twostep;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Setup state per user and application ({@code platform.user_two_step_state}): reminders, skips, deadline,
 * "don't ask again" and exemption. Each application has its own settings, so each keeps its own progress. The
 * row without an application ({@code applicationId} null) is for sign-ins without one, and its exemption covers
 * every application.
 */
@Repository
public class TwoStepStateStore {

    /** Stored application id of the row without an application. */
    static final UUID NO_APPLICATION = new UUID(0L, 0L);

    public record State(
            UUID userId,
            UUID applicationId,
            Instant firstPromptedAt,
            Instant nextPromptAt,
            int skipsUsed,
            Instant deadline,
            boolean optedOut,
            Instant exemptUntil,
            String exemptReason,
            String exemptedBy,
            String extendedBy,
            String remindersSent) {

        public static State empty(UUID userId, UUID applicationId) {
            return new State(userId, applicationId, null, null, 0, null, false, null, null, null, null, "");
        }

        public boolean exempt(Instant now) {
            return exemptUntil != null && exemptUntil.isAfter(now);
        }

        public State withProgress(Instant firstPromptedAt, Instant nextPromptAt, int skipsUsed, Instant deadline,
                boolean optedOut, String extendedBy, String remindersSent) {
            return new State(userId, applicationId, firstPromptedAt, nextPromptAt, skipsUsed, deadline, optedOut,
                    exemptUntil, exemptReason, exemptedBy, extendedBy, remindersSent);
        }

        public State withExemption(Instant until, String reason, String byWhom) {
            return new State(userId, applicationId, firstPromptedAt, nextPromptAt, skipsUsed, deadline, optedOut,
                    until, reason, byWhom, extendedBy, remindersSent);
        }

        public State withRemindersSent(String sent) {
            return withProgress(firstPromptedAt, nextPromptAt, skipsUsed, deadline, optedOut, extendedBy, sent);
        }
    }

    private final JdbcTemplate jdbc;

    public TwoStepStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The user's state in one application ({@code applicationId} null: the row without an application). */
    public State get(UUID userId, UUID applicationId) {
        return jdbc.query(
                        "SELECT * FROM platform.user_two_step_state WHERE user_id = ? AND application_id = ?",
                        TwoStepStateStore::map, userId, stored(applicationId))
                .stream()
                .findFirst()
                .orElse(State.empty(userId, applicationId));
    }

    /** Every row of the user: one per application where something happened, and the one without. */
    public List<State> forUser(UUID userId) {
        return jdbc.query("SELECT * FROM platform.user_two_step_state WHERE user_id = ?", TwoStepStateStore::map, userId);
    }

    public void save(State s) {
        jdbc.update(
                """
                INSERT INTO platform.user_two_step_state (user_id, application_id, first_prompted_at, next_prompt_at,
                    skips_used, deadline, opted_out, exempt_until, exempt_reason, exempted_by, extended_by,
                    reminders_sent, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id, application_id) DO UPDATE SET first_prompted_at = EXCLUDED.first_prompted_at,
                    next_prompt_at = EXCLUDED.next_prompt_at, skips_used = EXCLUDED.skips_used,
                    deadline = EXCLUDED.deadline, opted_out = EXCLUDED.opted_out, exempt_until = EXCLUDED.exempt_until,
                    exempt_reason = EXCLUDED.exempt_reason, exempted_by = EXCLUDED.exempted_by,
                    extended_by = EXCLUDED.extended_by, reminders_sent = EXCLUDED.reminders_sent, updated_at = now()
                """,
                s.userId(), stored(s.applicationId()), ts(s.firstPromptedAt()), ts(s.nextPromptAt()), s.skipsUsed(),
                ts(s.deadline()), s.optedOut(), ts(s.exemptUntil()), s.exemptReason(), s.exemptedBy(), s.extendedBy(),
                s.remindersSent() != null ? s.remindersSent() : "");
    }

    /**
     * Forgets reminders, skips and the deadline in every application: the user set up a method, which every
     * application of the tenant sees. Keeps exemptions.
     */
    public void completed(UUID userId) {
        jdbc.update(
                """
                UPDATE platform.user_two_step_state SET first_prompted_at = NULL, next_prompt_at = NULL, skips_used = 0,
                    deadline = NULL, opted_out = false, reminders_sent = '', updated_at = now() WHERE user_id = ?
                """,
                userId);
    }

    /**
     * Rows with a deadline in the next {@code days} days (reminder emails). Rows of a deleted application are
     * left out (the zero UUID of "no application" rules out a foreign key, so they are not removed with it).
     */
    public List<State> deadlinesWithin(int days) {
        return new ArrayList<>(jdbc.query(
                """
                SELECT s.* FROM platform.user_two_step_state s
                WHERE s.deadline IS NOT NULL AND s.deadline > now() AND s.deadline <= now() + make_interval(days => ?)
                  AND (s.application_id = ? OR EXISTS (SELECT 1 FROM platform.application a WHERE a.id = s.application_id))
                """,
                TwoStepStateStore::map,
                days,
                NO_APPLICATION));
    }

    private static UUID stored(UUID applicationId) {
        return applicationId != null ? applicationId : NO_APPLICATION;
    }

    private static Timestamp ts(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    private static State map(ResultSet rs, int i) throws SQLException {
        UUID app = rs.getObject("application_id", UUID.class);
        return new State(
                rs.getObject("user_id", UUID.class),
                NO_APPLICATION.equals(app) ? null : app,
                instant(rs, "first_prompted_at"),
                instant(rs, "next_prompt_at"),
                rs.getInt("skips_used"),
                instant(rs, "deadline"),
                rs.getBoolean("opted_out"),
                instant(rs, "exempt_until"),
                rs.getString("exempt_reason"),
                rs.getString("exempted_by"),
                rs.getString("extended_by"),
                rs.getString("reminders_sent"));
    }
}
