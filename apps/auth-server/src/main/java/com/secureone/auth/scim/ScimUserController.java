package com.secureone.auth.scim;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.webhook.UserLifecycleWebhookPublisher;
import jakarta.servlet.http.HttpServletRequest;
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

@Tag(name = "SCIM 2.0 provisioning", description = "Users and groups provisioned by an identity provider (SCIM bearer token)")
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
    public Map<String, Object> list(
            HttpServletRequest request,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String filter) {
        UUID scopeTenant = ScimScope.resolve(request, tenantId);
        java.util.function.Predicate<UserAccount> matches = filterPredicate(filter);
        List<Map<String, Object>> resources = users.findByTenantIdOrderByCreatedAtDesc(scopeTenant).stream()
                .filter(matches)
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
    public Map<String, Object> get(HttpServletRequest request, @PathVariable UUID id) {
        return toScimUser(requireUser(request, id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        UUID tenantId = ScimScope.resolve(request, parseTenantId(body));
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
        applyName(user, body.get("name"));
        user.setDisplayName(string(body.get("displayName"), formattedName(user, email)));
        user.setStatus(Boolean.FALSE.equals(body.get("active")) ? "DISABLED" : "ACTIVE");
        user.setType("USER");
        user = users.save(user);
        userLifecycleWebhooks.userCreated(user, null, "scim");
        return ResponseEntity.status(HttpStatus.CREATED).body(toScimUser(user));
    }

    @PutMapping("/{id}")
    public Map<String, Object> replace(HttpServletRequest request, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UserAccount user = requireUser(request, id);
        String status = user.getStatus();
        String email = user.getEmail();
        applyUserFields(user, body);
        UserAccount saved = users.save(user);
        endAccessIfDeactivated(saved, status, email);
        return toScimUser(saved);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> patch(HttpServletRequest request, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UserAccount user = requireUser(request, id);
        String status = user.getStatus();
        String email = user.getEmail();
        applyPatchOperations(user, body);
        UserAccount saved = users.save(user);
        endAccessIfDeactivated(saved, status, email);
        return toScimUser(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        UserAccount user = requireUser(request, id);
        String status = user.getStatus();
        user.setStatus("DISABLED");
        users.save(user);
        endAccessIfDeactivated(user, status, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.account.UserAccessTermination> accessTermination;

    /** Deprovisioned by the IdP: the user's refresh tokens, browser sessions and app sessions end too. */
    private void endAccessIfDeactivated(UserAccount user, String previousStatus, String previousEmail) {
        if ("ACTIVE".equalsIgnoreCase(previousStatus) && !"ACTIVE".equalsIgnoreCase(user.getStatus())
                && accessTermination != null) {
            accessTermination.ifAvailable(t -> t.endEverywhere(user, previousEmail));
        }
    }

    /** A user of the token's tenant; others are reported as not found. */
    private UserAccount requireUser(HttpServletRequest request, UUID id) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ScimScope.requireVisible(request, user.getTenantId());
        return user;
    }

    private static final java.util.regex.Pattern EQ_FILTER =
            java.util.regex.Pattern.compile("^\\s*([A-Za-z.]+)\\s+eq\\s+\"([^\"]*)\"\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** RFC 7644 filters used by IdPs before provisioning: userName / emails[.value] / displayName eq "x". */
    static java.util.function.Predicate<UserAccount> filterPredicate(String filter) {
        if (filter == null || filter.isBlank()) {
            return user -> true;
        }
        java.util.regex.Matcher m = EQ_FILTER.matcher(filter);
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported filter. Use: userName eq \"value\".");
        }
        String attribute = m.group(1).toLowerCase(Locale.ROOT);
        String value = m.group(2);
        return switch (attribute) {
            case "username", "emails", "emails.value" -> user ->
                    value.equalsIgnoreCase(user.getEmail()) || value.equalsIgnoreCase(user.getUsername());
            case "displayname" -> user -> value.equals(user.getDisplayName());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported filter attribute: " + m.group(1));
        };
    }

    /** SCIM "name": {"givenName", "familyName"}; a member left out keeps its value. */
    private static void applyName(UserAccount user, Object name) {
        if (name instanceof Map<?, ?> map) {
            String given = map.containsKey("givenName") ? stringOrNull(map.get("givenName")) : user.getGivenName();
            String family = map.containsKey("familyName") ? stringOrNull(map.get("familyName")) : user.getFamilyName();
            user.setNames(given, family);
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** "Given Family" from the stored names, or the fallback when there are none. */
    private static String formattedName(UserAccount user, String fallback) {
        String joined = ((user.getGivenName() != null ? user.getGivenName() : "") + " "
                + (user.getFamilyName() != null ? user.getFamilyName() : "")).trim();
        return joined.isEmpty() ? fallback : joined;
    }

    private void applyUserFields(UserAccount user, Map<String, Object> body) {
        if (body.containsKey("name")) {
            applyName(user, body.get("name"));
        }
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
        // Some IdP provisioning clients also send path-less replaces: {"op":"replace","value":{"active":false,...}}.
        List<Map<?, ?>> expanded = new ArrayList<>();
        for (Object raw : ops) {
            if (!(raw instanceof Map<?, ?> op)) {
                continue;
            }
            if (op.get("path") == null && op.get("value") instanceof Map<?, ?> attributes) {
                for (Map.Entry<?, ?> attribute : attributes.entrySet()) {
                    expanded.add(Map.of("op", String.valueOf(op.get("op")), "path", String.valueOf(attribute.getKey()),
                            "value", attribute.getValue() == null ? "" : attribute.getValue()));
                }
            } else {
                expanded.add(op);
            }
        }
        for (Map<?, ?> op : expanded) {
            String operation = String.valueOf(op.get("op")).toLowerCase(Locale.ROOT);
            if (!"replace".equals(operation)) {
                continue;
            }
            String path = op.get("path") != null ? String.valueOf(op.get("path")) : null;
            Object value = op.get("value");
            if ("active".equalsIgnoreCase(path)) {
                // Some clients send booleans as strings ("False").
                boolean active = !(Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(String.valueOf(value)));
                user.setStatus(active ? "ACTIVE" : "DISABLED");
            } else if ("displayName".equalsIgnoreCase(path)) {
                user.setDisplayName(string(value, user.getDisplayName()));
            } else if ("name".equalsIgnoreCase(path)) {
                applyName(user, value);
            } else if ("name.givenName".equalsIgnoreCase(path)) {
                user.setNames(stringOrNull(value), user.getFamilyName());
            } else if ("name.familyName".equalsIgnoreCase(path)) {
                user.setNames(user.getGivenName(), stringOrNull(value));
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
        if (user.getGivenName() != null || user.getFamilyName() != null) {
            Map<String, Object> name = new LinkedHashMap<>();
            if (user.getGivenName() != null) name.put("givenName", user.getGivenName());
            if (user.getFamilyName() != null) name.put("familyName", user.getFamilyName());
            name.put("formatted", formattedName(user, ""));
            out.put("name", name);
        }
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
        // Optional: a tenant SCIM token already determines the tenant (see ScimScope).
        return tenantId == null ? null : UUID.fromString(String.valueOf(tenantId));
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
