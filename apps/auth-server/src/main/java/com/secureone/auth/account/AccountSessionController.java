package com.secureone.auth.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Account sessions", description = "List and revoke browser sessions (session cookie)")
@RestController
@RequestMapping("/api/v1/account/sessions")
public class AccountSessionController {

    private final AccountSessionService sessions;

    public AccountSessionController(AccountSessionService sessions) {
        this.sessions = sessions;
    }

    @Operation(summary = "List active sessions", description = "Returns Redis-backed sessions for the signed-in principal.")
    @GetMapping
    public Map<String, Object> list(Authentication authentication, HttpServletRequest request) {
        String principal = requirePrincipal(authentication);
        String currentId = AccountSessionService.currentSessionId(request);
        return Map.of(
                "principal", principal,
                "currentSessionId", currentId != null ? currentId : "",
                "sessions",
                        sessions.list(principal, currentId).stream().map(this::toMap).toList());
    }

    @Operation(summary = "Revoke a session", description = "Invalidates the given session. Revoking the current session signs you out.")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(
            Authentication authentication, @PathVariable String sessionId) {
        sessions.revoke(requirePrincipal(authentication), sessionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Revoke other sessions",
            description = "Invalidates all sessions except the one used for this request.")
    @DeleteMapping
    public Map<String, Object> revokeOthers(Authentication authentication, HttpServletRequest request) {
        String principal = requirePrincipal(authentication);
        int revoked = sessions.revokeOthers(principal, AccountSessionService.currentSessionId(request));
        return Map.of("revoked", revoked);
    }

    private static String requirePrincipal(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
        }
        return authentication.getName();
    }

    private Map<String, Object> toMap(AccountSessionService.SessionView view) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", view.id());
        out.put("createdAt", view.createdAt().toString());
        out.put("lastAccessedAt", view.lastAccessedAt().toString());
        out.put("current", view.current());
        return out;
    }
}
