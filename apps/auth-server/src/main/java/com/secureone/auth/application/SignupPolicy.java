package com.secureone.auth.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An application's sign-up rules (its own; the platform has none): which email domains may sign up (allowed, or
 * blocked), and extra fields asked on the hosted sign-up page, saved as the user's attributes. Domain rules apply
 * to every new account made through the application: hosted sign-up and social or SAML just-in-time accounts.
 */
public record SignupPolicy(List<String> allowedDomains, List<String> blockedDomains, List<Field> fields) {

    public static final String SETTING_KEY = "signup_policy";

    public static final SignupPolicy NONE = new SignupPolicy(List.of(), List.of(), List.of());

    /** One extra sign-up field. {@code type}: text, select (one of {@code options}) or checkbox. */
    public record Field(String key, String label, String type, boolean required, List<String> options, int maxLength) {
        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("key", key);
            out.put("label", label);
            out.put("type", type);
            out.put("required", required);
            out.put("options", options);
            out.put("maxLength", maxLength);
            return out;
        }
    }

    static final int MAX_DOMAINS = 200;
    static final int MAX_FIELDS = 20;
    private static final Pattern DOMAIN = Pattern.compile("^(\\*\\.)?[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");
    private static final Pattern KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,39}$");
    private static final Set<String> TYPES = Set.of("text", "select", "checkbox");
    /** Attributes SecureOne keeps for itself, and profile fields with their own columns. */
    public static final Set<String> RESERVED_KEYS = Set.of(
            "pendingEmail", "pendingEmailApplicationId", "pendingInviteApplicationId", "authMethods",
            "email", "phone", "username", "password", "firstName", "lastName", "displayName", "status");

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowedDomains", allowedDomains);
        out.put("blockedDomains", blockedDomains);
        out.put("fields", fields.stream().map(Field::toMap).toList());
        return out;
    }

    public boolean isEmpty() {
        return allowedDomains.isEmpty() && blockedDomains.isEmpty() && fields.isEmpty();
    }

    /** Why this email may not get a new account here, or null when it may. */
    public String domainRefusal(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        String domain = at < 0 ? "" : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        if (blockedDomains.stream().anyMatch(d -> matches(d, domain))) {
            return "Sign-up with this email domain is not allowed.";
        }
        if (!allowedDomains.isEmpty() && allowedDomains.stream().noneMatch(d -> matches(d, domain))) {
            return "Sign-up is open only to email addresses of this application's organization.";
        }
        return null;
    }

    /** {@code example.com} is that domain only; {@code *.example.com} its subdomains (not the domain itself). */
    static boolean matches(String rule, String domain) {
        if (rule.startsWith("*.")) {
            return domain.endsWith(rule.substring(1));
        }
        return domain.equals(rule);
    }

    /**
     * The values a sign-up gave for the extra fields, checked: required ones present, selects one of their options,
     * text within its length. Unknown keys are ignored. Returns what to save in the user's attributes.
     */
    public Map<String, Object> attributesFrom(Map<String, Object> submitted) {
        Map<String, Object> in = submitted != null ? submitted : Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field f : fields) {
            Object raw = in.get(f.key());
            switch (f.type()) {
                case "checkbox" -> {
                    boolean checked = Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
                    if (f.required() && !checked) {
                        throw new IllegalArgumentException(f.label() + " must be accepted.");
                    }
                    out.put(f.key(), checked);
                }
                default -> {
                    String value = raw == null ? "" : String.valueOf(raw).trim();
                    if (value.isEmpty()) {
                        if (f.required()) {
                            throw new IllegalArgumentException(f.label() + " is required.");
                        }
                        continue;
                    }
                    if ("select".equals(f.type()) && !f.options().contains(value)) {
                        throw new IllegalArgumentException(f.label() + ": choose one of the options.");
                    }
                    if (value.length() > f.maxLength()) {
                        throw new IllegalArgumentException(f.label() + " is too long (at most " + f.maxLength() + " characters).");
                    }
                    out.put(f.key(), value);
                }
            }
        }
        return out;
    }

    /** What an administrator saved, checked and normalized; throws with what is wrong. */
    public static SignupPolicy sanitize(Map<String, Object> body) {
        if (body == null) {
            return NONE;
        }
        List<String> allowed = domains(body.get("allowedDomains"), "Allowed domains");
        List<String> blocked = domains(body.get("blockedDomains"), "Blocked domains");
        List<Field> fields = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        if (body.get("fields") instanceof List<?> list) {
            if (list.size() > MAX_FIELDS) {
                throw new IllegalArgumentException("At most " + MAX_FIELDS + " sign-up fields.");
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                String key = str(m.get("key"));
                if (!KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("Field key \"" + key + "\": a letter, then letters, digits or _ (up to 40).");
                }
                if (RESERVED_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Field key \"" + key + "\" is reserved.");
                }
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("Field key \"" + key + "\" is used twice.");
                }
                String label = str(m.get("label"));
                if (label.isEmpty() || label.length() > 80) {
                    throw new IllegalArgumentException("Field \"" + key + "\" needs a label (up to 80 characters).");
                }
                String type = str(m.get("type")).isEmpty() ? "text" : str(m.get("type")).toLowerCase(Locale.ROOT);
                if (!TYPES.contains(type)) {
                    throw new IllegalArgumentException("Field \"" + key + "\": type is text, select or checkbox.");
                }
                List<String> options = new ArrayList<>();
                if (m.get("options") instanceof List<?> opts) {
                    for (Object o : opts) {
                        String v = str(o);
                        if (!v.isEmpty() && v.length() <= 80 && !options.contains(v)) {
                            options.add(v);
                        }
                    }
                }
                if ("select".equals(type) && options.isEmpty()) {
                    throw new IllegalArgumentException("Field \"" + key + "\" is a list: give its options.");
                }
                int max = 200;
                if (m.get("maxLength") instanceof Number n) {
                    max = Math.max(1, Math.min(1000, n.intValue()));
                }
                fields.add(new Field(key, label, type, Boolean.TRUE.equals(m.get("required")),
                        "select".equals(type) ? List.copyOf(options) : List.of(), max));
            }
        }
        return new SignupPolicy(allowed, blocked, List.copyOf(fields));
    }

    /** A saved policy (already checked when saved; anything malformed is left out). */
    public static SignupPolicy parse(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return NONE;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) map;
            return sanitize(body);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    private static List<String> domains(Object raw, String what) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String d = str(item).toLowerCase(Locale.ROOT);
                if (d.startsWith("@")) {
                    d = d.substring(1);
                }
                if (d.isEmpty() || out.contains(d)) {
                    continue;
                }
                if (!DOMAIN.matcher(d).matches()) {
                    throw new IllegalArgumentException(what + ": \"" + d + "\" is not a domain (example.com or *.example.com).");
                }
                out.add(d);
            }
        }
        if (out.size() > MAX_DOMAINS) {
            throw new IllegalArgumentException(what + ": at most " + MAX_DOMAINS + ".");
        }
        return List.copyOf(out);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
