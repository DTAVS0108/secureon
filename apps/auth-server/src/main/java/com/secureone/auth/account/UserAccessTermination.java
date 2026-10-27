package com.secureone.auth.account;

import com.secureone.auth.oauth.BackchannelLogoutService;
import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Ends the ways one user is signed in: their OAuth grants (refresh tokens stop working, and userinfo refuses the
 * access tokens), their browser sessions (no new codes), and a back-channel logout to each application they used.
 * Access tokens already issued still pass local JWT checks until their normal (short) lifetime runs out.
 */
@Service
public class UserAccessTermination {

    private static final Logger log = LoggerFactory.getLogger(UserAccessTermination.class);

    private final JdbcTemplate jdbc;
    private final AccountSessionService sessions;
    private final BackchannelLogoutService backchannel;
    private final TenantRepository tenants;

    public UserAccessTermination(
            JdbcTemplate jdbc,
            AccountSessionService sessions,
            BackchannelLogoutService backchannel,
            TenantRepository tenants) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.backchannel = backchannel;
        this.tenants = tenants;
    }

    /** Same, for an account signed in with its current email. */
    public int endEverywhere(UserAccount user) {
        return endEverywhere(user, user.getEmail());
    }

    /** Same, for an account signed in with {@code email} (its address before a change saved with it). */
    public int endEverywhere(UserAccount user, String email) {
        String slug = RlsScope.asPlatform(() -> tenants.findById(user.getTenantId()).map(t -> t.getSlug()).orElse(null));
        return slug != null ? endEverywhere(slug + ":" + email, user.getId()) : 0;
    }

    /**
     * Everything, everywhere (suspended, disabled, deletion requested, signed out by an administrator). Returns the
     * number of browser sessions ended. Never throws: the change that called it must go ahead.
     */
    public int endEverywhere(String principal, UUID userId) {
        try {
            // Which applications to tell is read before their grants go.
            List<OAuthClient> clients = RlsScope.asPlatform(() -> backchannel.clientsToNotify(principal));
            RlsScope.runAsPlatform(() -> jdbc.update(
                    "DELETE FROM platform.oauth2_authorization WHERE principal_name = ?", principal));
            int ended = sessions.endAllQuietly(principal);
            if (!clients.isEmpty()) {
                backchannel.signedOutFrom(principal, userId, clients);
            }
            return ended;
        } catch (RuntimeException e) {
            log.warn("Access of {} not fully ended: {}", principal, e.getMessage());
            return 0;
        }
    }

    /**
     * One application only (the user is removed from it, or it is deleted): the grants and consents of its OAuth
     * clients end and those clients get a back-channel logout. The browser session stays (it serves the user's
     * other applications); the application's membership check refuses new codes. Returns the grants removed.
     */
    public int endInApplication(UserAccount user, UUID applicationId) {
        String slug = RlsScope.asPlatform(() -> tenants.findById(user.getTenantId()).map(t -> t.getSlug()).orElse(null));
        if (slug == null) {
            return 0;
        }
        String principal = slug + ":" + user.getEmail();
        try {
            return RlsScope.asPlatform(() -> {
                List<OAuthClient> clients = backchannel.clientsToNotify(principal).stream()
                        .filter(c -> applicationId.equals(c.getApplicationId()))
                        .toList();
                String ofApp = "registered_client_id IN (SELECT id::text FROM platform.oauth_client WHERE application_id = ?)";
                int removed = jdbc.update(
                        "DELETE FROM platform.oauth2_authorization WHERE principal_name = ? AND " + ofApp,
                        principal, applicationId);
                jdbc.update("DELETE FROM platform.oauth2_authorization_consent WHERE principal_name = ? AND " + ofApp,
                        principal, applicationId);
                if (!clients.isEmpty()) {
                    backchannel.signedOutFrom(principal, user.getId(), clients);
                }
                return removed;
            });
        } catch (RuntimeException e) {
            log.warn("Access of {} to application {} not fully ended: {}", principal, applicationId, e.getMessage());
            return 0;
        }
    }

    /**
     * Everyone signed in to an application that is being deleted: its clients' grants and consents end, and each
     * client with a back-channel logout URL is told for each of its users. Called before the clients are deleted.
     * Returns the number of users signed out.
     */
    public int endApplication(UUID applicationId) {
        try {
            return RlsScope.asPlatform(() -> {
                String ofApp = "registered_client_id IN (SELECT id::text FROM platform.oauth_client WHERE application_id = ?)";
                List<String> principals = jdbc.queryForList(
                        "SELECT DISTINCT principal_name FROM platform.oauth2_authorization WHERE " + ofApp,
                        String.class, applicationId);
                for (String principal : principals) {
                    List<OAuthClient> clients = backchannel.clientsToNotify(principal).stream()
                            .filter(c -> applicationId.equals(c.getApplicationId()))
                            .toList();
                    if (!clients.isEmpty()) {
                        backchannel.sessionSignedOut(principal, null, clients);
                    }
                }
                jdbc.update("DELETE FROM platform.oauth2_authorization WHERE " + ofApp, applicationId);
                jdbc.update("DELETE FROM platform.oauth2_authorization_consent WHERE " + ofApp, applicationId);
                return principals.size();
            });
        } catch (RuntimeException e) {
            log.warn("Sign-ins to application {} not fully ended: {}", applicationId, e.getMessage());
            return 0;
        }
    }

    /**
     * The grants given during one browser session ({@code sid} of its ID tokens), when that session logs out.
     * Grants with {@code offline_access} are meant to outlive the session and are kept.
     */
    public int endBrowserSession(String principal, String sid) {
        if (principal == null || sid == null || sid.isBlank()) {
            return 0;
        }
        try {
            return RlsScope.asPlatform(() -> jdbc.update("""
                    DELETE FROM platform.oauth2_authorization
                    WHERE principal_name = ?
                      AND position(? in coalesce(oidc_id_token_metadata, '')) > 0
                      AND (',' || coalesce(authorized_scopes, '') || ',') NOT LIKE '%,offline_access,%'
                    """, principal, "\"sid\":\"" + sid + "\""));
        } catch (RuntimeException e) {
            log.warn("Grants of the signed-out session of {} not removed: {}", principal, e.getMessage());
            return 0;
        }
    }
}
