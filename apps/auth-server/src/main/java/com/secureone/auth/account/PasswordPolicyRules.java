package com.secureone.auth.account;

import com.secureone.auth.application.PasswordPolicySanitizer;
import com.secureone.auth.user.UserCredential;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Builds public requirement labels and evaluates passwords against a policy map. */
public final class PasswordPolicyRules {

    private PasswordPolicyRules() {}

    public static Map<String, Object> publicPolicy(Map<String, Object> policy) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minLength", minLength(policy));
        out.put("requireUppercase", bool(policy, "requireUppercase"));
        out.put("requireNumber", bool(policy, "requireNumber"));
        out.put("requireSymbol", bool(policy, "requireSymbol"));
        out.put("expiryDays", expiryDays(policy));
        out.put("historyCount", historyCount(policy));
        out.put("hashAlgorithm", hashAlgorithm(policy));
        out.put("breachCheck", breachCheck(policy));
        out.put("requirements", requirements(policy));
        return out;
    }

    public static Map<String, Object> signupPolicy(Map<String, Object> policy) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minLength", minLength(policy));
        out.put("requireUppercase", bool(policy, "requireUppercase"));
        out.put("requireNumber", bool(policy, "requireNumber"));
        out.put("requireSymbol", bool(policy, "requireSymbol"));
        out.put("expiryDays", expiryDays(policy));
        out.put("historyCount", historyCount(policy));
        out.put("requirements", requirements(policy));
        return out;
    }

    public static List<Map<String, Object>> requirements(Map<String, Object> policy) {
        List<Map<String, Object>> rules = new ArrayList<>();
        int minLength = minLength(policy);
        rules.add(rule("minLength", "At least " + minLength + " characters"));
        if (bool(policy, "requireUppercase")) {
            rules.add(rule("uppercase", "One uppercase letter (A–Z)"));
        }
        if (bool(policy, "requireNumber")) {
            rules.add(rule("number", "One number (0–9)"));
        }
        if (bool(policy, "requireSymbol")) {
            rules.add(rule("symbol", "One symbol (!@#$… )"));
        }
        int history = historyCount(policy);
        if (history > 0) {
            rules.add(rule("history", "Must not match your last " + history + " password(s)"));
        }
        if (breachCheck(policy)) {
            rules.add(rule("breachCheck", "Must not appear in known data breaches"));
        }
        int expiry = expiryDays(policy);
        if (expiry > 0) {
            rules.add(rule("expiry", "Password expires after " + expiry + " day(s)"));
        }
        return rules;
    }

    public static void validate(String password, Map<String, Object> policy) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required.");
        }
        int minLength = minLength(policy);
        if (password.length() < minLength) {
            throw new IllegalArgumentException("Password must be at least " + minLength + " characters.");
        }
        if (bool(policy, "requireUppercase") && !Pattern.compile("[A-Z]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must include an uppercase letter.");
        }
        if (bool(policy, "requireNumber") && !Pattern.compile("[0-9]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must include a number.");
        }
        if (bool(policy, "requireSymbol") && !Pattern.compile("[^a-zA-Z0-9]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must include a symbol.");
        }
        hashAlgorithm(policy);
    }

    public static void validateNotInHistory(
            String password, List<UserCredential> previous, PasswordEncoder encoder, Map<String, Object> policy) {
        int limit = historyCount(policy);
        if (limit <= 0 || previous == null || previous.isEmpty()) {
            return;
        }
        int checked = 0;
        for (UserCredential credential : previous) {
            if (checked >= limit) {
                break;
            }
            if (encoder.matches(password, credential.getPasswordHash())) {
                throw new IllegalArgumentException(
                        "Password was used recently. Choose a password you have not used in your last "
                                + limit
                                + " password(s).");
            }
            checked++;
        }
    }

    public static Instant expiresAtForNewCredential(Map<String, Object> policy, Instant now) {
        int expiryDays = expiryDays(policy);
        if (expiryDays <= 0) {
            return null;
        }
        return now.plus(expiryDays, ChronoUnit.DAYS);
    }

    public static boolean isExpired(UserCredential credential, Instant now) {
        return credential != null
                && credential.getExpiresAt() != null
                && !now.isBefore(credential.getExpiresAt());
    }

    /** Days before expiry when a warning email is sent (capped by total expiry period). */
    public static int expiryWarningLeadDays(Map<String, Object> policy) {
        int expiry = expiryDays(policy);
        if (expiry <= 0) {
            return 0;
        }
        return Math.min(7, expiry);
    }

    public static boolean isExpiringSoon(UserCredential credential, Map<String, Object> policy, Instant now) {
        if (credential == null || credential.getExpiresAt() == null || isExpired(credential, now)) {
            return false;
        }
        int leadDays = expiryWarningLeadDays(policy);
        if (leadDays <= 0) {
            return false;
        }
        Instant warningStart = credential.getExpiresAt().minus(leadDays, ChronoUnit.DAYS);
        return !now.isBefore(warningStart);
    }

    public static List<Map<String, Object>> evaluate(String password, Map<String, Object> policy) {
        String value = password != null ? password : "";
        List<Map<String, Object>> out = new ArrayList<>();
        int minLength = minLength(policy);
        out.add(evaluation("minLength", "At least " + minLength + " characters", value.length() >= minLength));
        if (bool(policy, "requireUppercase")) {
            out.add(evaluation(
                    "uppercase",
                    "One uppercase letter (A–Z)",
                    Pattern.compile("[A-Z]").matcher(value).find()));
        }
        if (bool(policy, "requireNumber")) {
            out.add(evaluation(
                    "number", "One number (0–9)", Pattern.compile("[0-9]").matcher(value).find()));
        }
        if (bool(policy, "requireSymbol")) {
            out.add(evaluation(
                    "symbol",
                    "One symbol (!@#$… )",
                    Pattern.compile("[^a-zA-Z0-9]").matcher(value).find()));
        }
        return out;
    }

    public static int minLength(Map<String, Object> policy) {
        return Math.max(8, number(policy, "minLength", 12));
    }

    public static int expiryDays(Map<String, Object> policy) {
        return Math.max(0, number(policy, "expiryDays", 0));
    }

    public static int historyCount(Map<String, Object> policy) {
        return Math.max(0, number(policy, "historyCount", 0));
    }

    public static boolean breachCheck(Map<String, Object> policy) {
        return PasswordPolicySanitizer.breachCheckEnabled(policy);
    }

    public static String hashAlgorithm(Map<String, Object> policy) {
        return PasswordPolicySanitizer.normalizeHashAlgorithm(policy != null ? policy.get("hashAlgorithm") : null);
    }

    private static Map<String, Object> rule(String key, String label) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("label", label);
        return row;
    }

    private static Map<String, Object> evaluation(String key, String label, boolean met) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("label", label);
        row.put("met", met);
        return row;
    }

    private static int number(Map<String, Object> map, String key, int fallback) {
        Object v = map != null ? map.get(key) : null;
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static boolean bool(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map != null ? map.get(key) : null);
    }
}
