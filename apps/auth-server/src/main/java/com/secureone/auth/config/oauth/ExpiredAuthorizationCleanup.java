package com.secureone.auth.config.oauth;

import com.secureone.auth.tenant.RlsScope;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes authorizations whose every token (code, access, ID, refresh, device) expired more than
 * {@code secureone.oauth.expired-retention} ago. Nothing else removed them, so each issued token stayed in
 * oauth2_authorization for good and the table grew with traffic. The grace period keeps recent rows for
 * refresh-token reuse detection and support questions. Runs in batches so it never locks the table for long;
 * safe on several instances at once.
 */
@Component
public class ExpiredAuthorizationCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthorizationCleanup.class);
    static final int BATCH = 5_000;
    static final String LAST_EXPIRY = "GREATEST(authorization_code_expires_at, access_token_expires_at,"
            + " oidc_id_token_expires_at, refresh_token_expires_at, user_code_expires_at, device_code_expires_at)";

    private final JdbcTemplate jdbc;
    private final Duration retention;

    public ExpiredAuthorizationCleanup(
            JdbcTemplate jdbc, @Value("${secureone.oauth.expired-retention:1d}") Duration retention) {
        this.jdbc = jdbc;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${secureone.oauth.expired-cleanup-interval:15m}", initialDelay = 120_000)
    public void removeExpired() {
        try {
            int removed = RlsScope.asPlatform(this::removeAll);
            if (removed > 0) {
                log.info("Expired OAuth authorizations removed: {}", removed);
            }
        } catch (RuntimeException e) {
            log.warn("Expired OAuth authorizations not removed: {}", e.getMessage());
        }
    }

    int removeAll() {
        int total = 0;
        int removed;
        do {
            removed = jdbc.update("DELETE FROM platform.oauth2_authorization WHERE id IN ("
                    + "SELECT id FROM platform.oauth2_authorization WHERE " + LAST_EXPIRY
                    + " < now() - make_interval(secs => ?) LIMIT " + BATCH + ")", retention.toSeconds());
            total += removed;
        } while (removed == BATCH);
        return total;
    }
}
