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
