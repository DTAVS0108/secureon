package com.secureone.auth.audit.stream;

import com.secureone.auth.tenant.RlsScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes audit entries and sign-in history older than the retention set under Platform settings › Logs &
 * streaming (30 to 3650 days, default 365), and webhook deliveries older than 30 days. Runs daily; running it on
 * several servers at once is harmless.
 */
@Component
public class LogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);
    static final int BATCH = 5000;

    private final LogStreamService streams;
    private final JdbcTemplate jdbc;

    public LogRetentionJob(LogStreamService streams, JdbcTemplate jdbc) {
        this.streams = streams;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${secureone.logs.retention-cron:0 20 3 * * *}")
    public void purge() {
        int audit = deleteOlderThan("audit_log", streams.auditRetentionDays());
        int signIns = deleteOlderThan("login_history", streams.signInHistoryRetentionDays());
        int deliveries = deleteOlderThan("webhook_delivery", 30);
        if (audit + signIns + deliveries > 0) {
            log.info("Log retention: removed {} audit entries, {} sign-ins, {} webhook deliveries", audit, signIns, deliveries);
        }
    }

    /**
     * Deletes in batches of {@value #BATCH}, each its own short transaction, so a first run over years of history
     * does not hold locks on the table or build one huge transaction.
     */
    int deleteOlderThan(String table, int days) {
        String sql = "DELETE FROM platform." + table + " WHERE id IN (SELECT id FROM platform." + table
                + " WHERE created_at < now() - make_interval(days => ?) LIMIT " + BATCH + ")";
        int total = 0;
        while (true) {
            Integer removed = RlsScope.asPlatform(() -> jdbc.update(sql, days));
            total += removed;
            if (removed < BATCH) {
                return total;
            }
        }
    }
}
