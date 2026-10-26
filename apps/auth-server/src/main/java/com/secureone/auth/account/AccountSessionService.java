package com.secureone.auth.account;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountSessionService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> auditLog;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.BackchannelLogoutService> backchannelLogout;

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public AccountSessionService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public record SessionView(String id, Instant createdAt, Instant lastAccessedAt, boolean current) {}

    public List<SessionView> list(String principalName, String currentSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(principalName);
        List<SessionView> views = new ArrayList<>();
        for (Map.Entry<String, ? extends Session> entry : found.entrySet()) {
            views.add(toView(entry.getValue(), entry.getKey(), currentSessionId));
        }
        views.sort(Comparator.comparing(SessionView::lastAccessedAt).reversed());
        return views;
    }

    public void revoke(String principalName, String sessionId) {
        assertOwned(principalName, sessionId);
        sessions.deleteById(sessionId);
        auditEnded(principalName, 1);
    }

    /** Ended sessions go in the audit log (and to webhooks as session.revoked). */
    private void auditEnded(String principalName, int count) {
        if (count <= 0) {
            return;
        }
        if (auditLog != null) auditLog.ifAvailable(a -> {
            try {
                String email = principalName != null && principalName.contains(":")
                        ? principalName.substring(principalName.indexOf(':') + 1)
                        : principalName;
                a.recordIndependently(null, null, email, "session.revoked", "session", null, count + " session(s)", true);
            } catch (RuntimeException ignored) {
                // Never stops the sign-out.
            }
        });
    }

    public int revokeOthers(String principalName, String currentSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(principalName);
        int count = 0;
        for (String id : found.keySet()) {
            if (currentSessionId != null && currentSessionId.equals(id)) {
                continue;
            }
            sessions.deleteById(id);
            count++;
        }
        auditEnded(principalName, count);
        if (currentSessionId == null && backchannelLogout != null) {
            // Every session ended (admin reset, two-step reset, SAML logout): the applications are told too. Not
            // when the user signs out other devices, since the apps on this device would end their sessions as well.
            backchannelLogout.ifAvailable(b -> b.signedOut(principalName));
        }
        return count;
    }

    private void assertOwned(String principalName, String sessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(principalName);
        if (!found.containsKey(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found.");
        }
    }

    private static SessionView toView(Session session, String id, String currentSessionId) {
        return new SessionView(
                id, session.getCreationTime(), session.getLastAccessedTime(), id.equals(currentSessionId));
    }

    public static String currentSessionId(HttpServletRequest request) {
        var httpSession = request.getSession(false);
        return httpSession != null ? httpSession.getId() : null;
    }
}
