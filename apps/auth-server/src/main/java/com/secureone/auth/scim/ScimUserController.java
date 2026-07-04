package com.secureone.auth.scim;

import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.webhook.UserLifecycleWebhookPublisher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/scim/v2/Users")
public class ScimUserController {

    private final UserAccountRepository users;
    private final UserLifecycleWebhookPublisher userLifecycleWebhooks;

    public ScimUserController(UserAccountRepository users, UserLifecycleWebhookPublisher userLifecycleWebhooks) {
        this.users = users;
        this.userLifecycleWebhooks = userLifecycleWebhooks;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) UUID tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId filter is required.");
        }
        List<Map<String, Object>> resources = users.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toScimUser)
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        out.put("totalResults", resources.size());
        out.put("itemsPerPage", resources.size());
        out.put("startIndex", 1);
        out.put("Resources", resources);
        return out;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toScimUser(user);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tenantId = parseTenantId(body);
        String email = extractEmail(body);
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userName or emails required.");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
        }
        UserAccount user = new UserAccount();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setUsername(email);
        user.setDisplayName(string(body.get("displayName"), email));
        user.setStatus(Boolean.FALSE.equals(body.get("active")) ? "DISABLED" : "ACTIVE");
        user.setType("USER");
        user = users.save(user);
        userLifecycleWebhooks.userCreated(user, null, "scim");
        return ResponseEntity.status(HttpStatus.CREATED).body(toScimUser(user));
    }

    @PutMapping("/{id}")
    public Map<String, Object> replace(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyUserFields(user, body);
        return toScimUser(users.save(user));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> patch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyPatchOperations(user, body);
        return toScimUser(users.save(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setStatus("DISABLED");
        users.save(user);
        return ResponseEntity.noContent().build();
    }

    private void applyUserFields(UserAccount user, Map<String, Object> body) {
        if (body.containsKey("displayName")) {
            user.setDisplayName(string(body.get("displayName"), user.getDisplayName()));
        }
        if (body.containsKey("active")) {
            user.setStatus(Boolean.FALSE.equals(body.get("active")) ? "DISABLED" : "ACTIVE");
        }
        String email = extractEmail(body);
        if (email != null && !email.isBlank()) {
            email = email.trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail())
                    && users.findByTenantIdAndEmail(user.getTenantId(), email).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use.");
            }
            user.setEmail(email);
            user.setUsername(email);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPatchOperations(UserAccount user, Map<String, Object> body) {
        Object operations = body.get("Operations");
        if (!(operations instanceof List<?> ops)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operations array is required.");
        }
        for (Object raw : ops) {
            if (!(raw instanceof Map<?, ?> op)) {
                continue;
            }
            String operation = String.valueOf(op.get("op")).toLowerCase(Locale.ROOT);
            if (!"replace".equals(operation)) {
                continue;
            }
            String path = op.get("path") != null ? String.valueOf(op.get("path")) : null;
            Object value = op.get("value");
            if ("active".equalsIgnoreCase(path)) {
                user.setStatus(Boolean.FALSE.equals(value) ? "DISABLED" : "ACTIVE");
            } else if ("displayName".equalsIgnoreCase(path)) {
                user.setDisplayName(string(value, user.getDisplayName()));
            } else if (path != null && path.toLowerCase(Locale.ROOT).startsWith("emails")) {
                if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                    String email = String.valueOf(first.get("value")).trim().toLowerCase(Locale.ROOT);
                    if (!email.isBlank()) {
                        if (!email.equalsIgnoreCase(user.getEmail())
                                && users.findByTenantIdAndEmail(user.getTenantId(), email).isPresent()) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use.");
                        }
                        user.setEmail(email);
                        user.setUsername(email);
                    }
                } else if (value instanceof Map<?, ?> map) {
                    String email = String.valueOf(map.get("value")).trim().toLowerCase(Locale.ROOT);
                    if (!email.isBlank()) {
                        if (!email.equalsIgnoreCase(user.getEmail())
                                && users.findByTenantIdAndEmail(user.getTenantId(), email).isPresent()) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use.");
                        }
                        user.setEmail(email);
                        user.setUsername(email);
                    }
                }
            }
        }
    }

    private Map<String, Object> toScimUser(UserAccount user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        out.put("id", user.getId().toString());
        out.put("userName", user.getEmail());
        out.put("displayName", user.getDisplayName());
        out.put("active", "ACTIVE".equalsIgnoreCase(user.getStatus()));
        out.put("emails", List.of(Map.of("value", user.getEmail(), "primary", true)));
        out.put("meta", Map.of("resourceType", "User"));
        out.put("tenantId", user.getTenantId().toString());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static UUID parseTenantId(Map<String, Object> body) {
        Object ext = body.get("urn:secureone:params:scim:schemas:extension:tenant:2.0:User");
        if (ext instanceof Map<?, ?> map && map.get("tenantId") != null) {
            return UUID.fromString(String.valueOf(map.get("tenantId")));
        }
        Object tenantId = body.get("tenantId");
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId extension is required.");
        }
        return UUID.fromString(String.valueOf(tenantId));
    }

    @SuppressWarnings("unchecked")
    private static String extractEmail(Map<String, Object> body) {
        if (body.get("userName") != null) {
            return String.valueOf(body.get("userName"));
        }
        Object emails = body.get("emails");
        if (emails instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            return String.valueOf(first.get("value"));
        }
        return null;
    }

    private static String string(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? fallback : value;
    }
}
