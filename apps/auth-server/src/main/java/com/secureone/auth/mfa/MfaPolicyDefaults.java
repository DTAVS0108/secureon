package com.secureone.auth.mfa;

import com.secureone.auth.twostep.TwoStepMethod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two-step verification policy (setting {@code mfa_policy}): defaults, validation, and the lock modes a higher
 * level (platform, then tenant) sets for the levels below.
 */
public final class MfaPolicyDefaults {

    public static final String LOCKS = "locks";
    public static final String LOCK_OPEN = "open";
    public static final String LOCK_STRICTER = "stricter";
    public static final String LOCK_LOCKED = "locked";

    public static final List<String> MODES = List.of("off", "optional", "required");
    public static final List<String> STRENGTHS = List.of("any", "strong", "phishing_resistant");

    /** Settings a lock mode can be set on (everything a lower level may override). */
    public static final List<String> LOCKABLE = List.of(
            "mode", "minimumStrength", "methods", "defaultMethod", "promptOptional", "promptIntervalDays",
            "allowDontAskAgain", "graceDays", "graceSkips", "enforceFrom", "reminderDays", "rememberDeviceDays",
            "challengeTtlSeconds", "maxChallengeAttempts", "recoveryCodesPerEnrollment", "freshStepMinutes",
            "stepUpAfterPassword");

    /**
     * Settings decided by the platform and the tenant only: they act on what a user has once for every
     * application (backup codes), so one application's value cannot apply.
     */
    public static final Set<String> NOT_PER_APPLICATION = Set.of("recoveryCodesPerEnrollment");

    private MfaPolicyDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("mode", "optional");
        policy.put("minimumStrength", "strong");
        policy.put("methods", List.of("passkey", "security_key", "totp"));
        policy.put("defaultMethod", "passkey");
        policy.put("promptOptional", true);
        policy.put("promptIntervalDays", 7);
        policy.put("allowDontAskAgain", false);
        policy.put("graceDays", 14);
        policy.put("graceSkips", 3);
        policy.put("enforceFrom", "");
        policy.put("reminderDays", List.of(7, 1));
        policy.put("rememberDeviceDays", 0);
        policy.put("challengeTtlSeconds", 300);
        policy.put("maxChallengeAttempts", 5);
        policy.put("recoveryCodesPerEnrollment", 10);
        policy.put("freshStepMinutes", 5);
        policy.put("stepUpAfterPassword", true);
        policy.put(LOCKS, Map.of());
        return policy;
    }

    /** Platform and application (no tenant layer, no locks): kept for callers that only know these two. */
    public static Map<String, Object> merge(Map<String, Object> platform, Map<String, Object> override) {
        Map<String, Object> out = new LinkedHashMap<>(platformDefaults());
        if (platform != null) {
            out.putAll(sanitizeForSave(platform));
        }
        if (override != null) {
            override.forEach((k, v) -> {
                if (v != null && !LOCKS.equals(k)) {
                    out.put(k, v);
                }
            });
        }
        return sanitizeForSave(out);
    }

    /** A complete, valid policy from any input: unknown keys dropped, values clamped, defaults filled in. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitizeForSave(Map<String, Object> body) {
        Map<String, Object> in = body != null ? body : Map.of();
        Map<String, Object> d = platformDefaults();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", oneOf(in.get("mode"), MODES, (String) d.get("mode")));
        out.put("minimumStrength", oneOf(in.get("minimumStrength"), STRENGTHS, (String) d.get("minimumStrength")));
        List<String> methods = methods(in.containsKey("methods") ? in.get("methods") : d.get("methods"));
        if (methods.isEmpty()) {
            methods = List.of("totp");
        }
        out.put("methods", methods);
        String defaultMethod = in.get("defaultMethod") instanceof String s && methods.contains(s) ? s : methods.get(0);
        out.put("defaultMethod", defaultMethod);
        copyBoolean(in, out, "promptOptional", d);
        copyInt(in, out, "promptIntervalDays", d, 1, 90);
        copyBoolean(in, out, "allowDontAskAgain", d);
        copyInt(in, out, "graceDays", d, 0, 180);
        copyInt(in, out, "graceSkips", d, 0, 10);
        out.put("enforceFrom", date(in.get("enforceFrom")));
        out.put("reminderDays", reminderDays(in.containsKey("reminderDays") ? in.get("reminderDays") : d.get("reminderDays")));
        copyInt(in, out, "rememberDeviceDays", d, 0, 90);
        copyInt(in, out, "challengeTtlSeconds", d, 60, 600); // codes sent by email or text: at most 10 minutes
        copyInt(in, out, "maxChallengeAttempts", d, 1, 10);
        copyInt(in, out, "recoveryCodesPerEnrollment", d, 1, 20);
        copyInt(in, out, "freshStepMinutes", d, 1, 60);
        copyBoolean(in, out, "stepUpAfterPassword", d);
        out.put(LOCKS, locks(in.get(LOCKS)));
        return out;
    }

    /** Only the keys present in {@code body}, each validated: what a tenant or application stores as overrides. */
    public static Map<String, Object> sanitizeOverride(Map<String, Object> body) {
        Map<String, Object> full = sanitizeForSave(body);
        Map<String, Object> out = new LinkedHashMap<>();
        if (body == null) {
            return out;
        }
        for (String key : full.keySet()) {
            if (body.containsKey(key) && body.get(key) != null) {
                out.put(key, full.get(key));
            }
        }
        return out;
    }

    // ---- Lock modes ----------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, String> locks(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                String key = String.valueOf(k);
                String mode = String.valueOf(v);
                if (LOCKABLE.contains(key) && (LOCK_LOCKED.equals(mode) || LOCK_STRICTER.equals(mode))) {
                    out.put(key, mode);
                }
            });
        }
        return out;
    }

    /** The tighter of two lock modes for each key (locked > stricter > open). */
    public static Map<String, String> combineLocks(Map<String, String> upper, Map<String, String> lower) {
        Map<String, String> out = new LinkedHashMap<>(lower);
        upper.forEach((key, mode) -> out.merge(key, mode, (a, b) -> rank(a) >= rank(b) ? a : b));
        return out;
    }

    private static int rank(String mode) {
        return LOCK_LOCKED.equals(mode) ? 2 : LOCK_STRICTER.equals(mode) ? 1 : 0;
    }

    /**
     * Applies a lower level's overrides on top of {@code base} under {@code locks}: locked keys keep the base
     * value, "stricter" keys take the override only when it is at least as strict.
     */
    public static Map<String, Object> applyLayer(
            Map<String, Object> base, Map<String, String> locks, Map<String, Object> overrides) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        if (overrides == null) {
            return out;
        }
        overrides.forEach((key, value) -> {
            if (value == null || LOCKS.equals(key) || !base.containsKey(key)) {
                return;
            }
            String lock = locks.getOrDefault(key, LOCK_OPEN);
            if (LOCK_LOCKED.equals(lock)) {
                return;
            }
            if (LOCK_STRICTER.equals(lock) && !atLeastAsStrict(key, value, base.get(key))) {
                return;
            }
            out.put(key, value);
        });
        return sanitizeForSave(out);
    }

    /** Keys whose override the level above does not allow, with why: shown to the admin who tries to save. */
    public static List<String> violations(
            Map<String, Object> base, Map<String, String> locks, Map<String, Object> overrides) {
        List<String> out = new ArrayList<>();
        if (overrides == null) {
            return out;
        }
        overrides.forEach((key, value) -> {
            if (value == null || LOCKS.equals(key) || !base.containsKey(key) || value.equals(base.get(key))) {
                return;
            }
            String lock = locks.getOrDefault(key, LOCK_OPEN);
            if (LOCK_LOCKED.equals(lock)) {
                out.add(key + " is set by the level above and cannot be changed");
            } else if (LOCK_STRICTER.equals(lock) && !atLeastAsStrict(key, value, base.get(key))) {
                out.add(key + " can only be made stricter than " + base.get(key));
            }
        });
        return out;
    }

    /**
     * What {@code after} makes weaker than {@code before}, in words (e.g. "mode: required → off", "unlocks mode"):
     * these changes wait for a second admin's approval. Empty when the change only keeps or tightens protection.
     * {@code after} may hold only some keys (the ones being changed).
     */
    public static List<String> weakenings(Map<String, Object> before, Map<String, Object> after) {
        List<String> out = new ArrayList<>();
        for (String key : LOCKABLE) {
            if (!after.containsKey(key) || after.get(key) == null) {
                continue;
            }
            Object was = before.get(key);
            Object now = after.get(key);
            if (!now.equals(was) && !atLeastAsStrict(key, now, was)) {
                out.add(key + ": " + was + " → " + now);
            }
        }
        if (after.containsKey(LOCKS)) {
            Map<String, String> wasLocks = locks(before.get(LOCKS));
            Map<String, String> nowLocks = locks(after.get(LOCKS));
            wasLocks.forEach((key, mode) -> {
                String next = nowLocks.getOrDefault(key, LOCK_OPEN);
                if (rank(next) < rank(mode)) {
                    out.add("lock on " + key + ": " + mode + " → " + next);
                }
            });
        }
        return out;
    }

    /** Whether {@code candidate} protects at least as much as {@code current} for setting {@code key}. */
    @SuppressWarnings("unchecked")
    public static boolean atLeastAsStrict(String key, Object candidate, Object current) {
        return switch (key) {
            case "mode" -> MODES.indexOf(String.valueOf(candidate)) >= MODES.indexOf(String.valueOf(current));
            case "minimumStrength" ->
                    STRENGTHS.indexOf(String.valueOf(candidate)) >= STRENGTHS.indexOf(String.valueOf(current));
            case "methods" -> current instanceof List<?> cur && candidate instanceof List<?> cand
                    && cur.containsAll(cand);
            case "promptOptional", "stepUpAfterPassword" ->
                    Boolean.TRUE.equals(candidate) || !Boolean.TRUE.equals(current);
            case "allowDontAskAgain" ->
                    !Boolean.TRUE.equals(candidate) || Boolean.TRUE.equals(current);
            case "promptIntervalDays", "graceDays", "graceSkips", "rememberDeviceDays", "challengeTtlSeconds",
                    "maxChallengeAttempts", "freshStepMinutes" -> number(candidate) <= number(current);
            case "enforceFrom" -> {
                String c = String.valueOf(candidate);
                String b = String.valueOf(current);
                yield !c.isBlank() && (b.isBlank() || c.compareTo(b) <= 0);
            }
            default -> true; // defaultMethod, reminderDays, recoveryCodesPerEnrollment: no stricter direction
        };
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static List<String> methods(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String id = String.valueOf(item);
                if (TwoStepMethod.IDS.contains(id)) {
                    out.add(id);
                }
            }
        }
        // Catalog order, so the strongest come first whatever order the admin ticked them in.
        return TwoStepMethod.IDS.stream().filter(out::contains).toList();
    }

    private static List<Integer> reminderDays(Object raw) {
        Set<Integer> out = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                int days = item instanceof Number n ? n.intValue() : parseInt(String.valueOf(item));
                if (days >= 1 && days <= 60) {
                    out.add(days);
                }
            }
        }
        return List.copyOf(out);
    }

    private static String date(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return "";
        }
        try {
            return LocalDate.parse(String.valueOf(raw).trim().substring(0, 10)).toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String oneOf(Object raw, List<String> allowed, String fallback) {
        String value = raw != null ? String.valueOf(raw).trim().toLowerCase() : "";
        return allowed.contains(value) ? value : fallback;
    }

    private static int number(Object raw) {
        return raw instanceof Number n ? n.intValue() : parseInt(String.valueOf(raw));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static void copyBoolean(
            Map<String, Object> body, Map<String, Object> out, String key, Map<String, Object> defaults) {
        Object raw = body.get(key);
        out.put(key, raw instanceof Boolean b ? b : raw != null ? Boolean.parseBoolean(String.valueOf(raw)) : defaults.get(key));
    }

    private static void copyInt(
            Map<String, Object> body,
            Map<String, Object> out,
            String key,
            Map<String, Object> defaults,
            int min,
            int max) {
        Object raw = body.containsKey(key) && body.get(key) != null ? body.get(key) : defaults.get(key);
        int value = number(raw);
        out.put(key, Math.max(min, Math.min(max, value)));
    }
}
