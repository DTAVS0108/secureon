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

/** Per-user setup state ({@code platform.user_two_step_state}): reminders, skips, deadline, exemption. */
@Repository
public class TwoStepStateStore {

    public record State(
            UUID userId,
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

        public static State empty(UUID userId) {
            return new State(userId, null, null, 0, null, false, null, null, null, null, "");
        }

        public boolean exempt(Instant now) {
            return exemptUntil != null && exemptUntil.isAfter(now);
        }
    }

    private final JdbcTemplate jdbc;

    public TwoStepStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public State get(UUID userId) {
        return jdbc.query("SELECT * FROM platform.user_two_step_state WHERE user_id = ?", TwoStepStateStore::map, userId)
                .stream()
                .findFirst()
                .orElse(State.empty(userId));
    }

    public void save(State s) {
        jdbc.update(
                """
                INSERT INTO platform.user_two_step_state (user_id, first_prompted_at, next_prompt_at, skips_used, deadline,
                    opted_out, exempt_until, exempt_reason, exempted_by, extended_by, reminders_sent, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET first_prompted_at = EXCLUDED.first_prompted_at,
                    next_prompt_at = EXCLUDED.next_prompt_at, skips_used = EXCLUDED.skips_used,
                    deadline = EXCLUDED.deadline, opted_out = EXCLUDED.opted_out, exempt_until = EXCLUDED.exempt_until,
                    exempt_reason = EXCLUDED.exempt_reason, exempted_by = EXCLUDED.exempted_by,
                    extended_by = EXCLUDED.extended_by, reminders_sent = EXCLUDED.reminders_sent, updated_at = now()
                """,
                s.userId(), ts(s.firstPromptedAt()), ts(s.nextPromptAt()), s.skipsUsed(), ts(s.deadline()), s.optedOut(),
                ts(s.exemptUntil()), s.exemptReason(), s.exemptedBy(), s.extendedBy(),
                s.remindersSent() != null ? s.remindersSent() : "");
    }

    /** Forgets reminders, skips and the deadline (a method was set up); keeps an exemption. */
    public void completed(UUID userId) {
        jdbc.update(
                """
                UPDATE platform.user_two_step_state SET first_prompted_at = NULL, next_prompt_at = NULL, skips_used = 0,
                    deadline = NULL, opted_out = false, reminders_sent = '', updated_at = now() WHERE user_id = ?
                """,
                userId);
    }

    /** Users with a deadline in the next {@code days} days (reminder emails). */
    public List<State> deadlinesWithin(int days) {
        List<State> out = new ArrayList<>(jdbc.query(
                """
                SELECT * FROM platform.user_two_step_state
                WHERE deadline IS NOT NULL AND deadline > now() AND deadline <= now() + make_interval(days => ?)
                """,
                TwoStepStateStore::map,
                days));
        return out;
    }

    private static Timestamp ts(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    private static State map(ResultSet rs, int i) throws SQLException {
        return new State(
                rs.getObject("user_id", UUID.class),
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
