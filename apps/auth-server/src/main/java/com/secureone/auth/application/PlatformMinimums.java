package com.secureone.auth.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An application's own password, verification and token settings may be stricter than the platform's, never weaker.
 * The platform values are the floor: {@code apply} raises a weaker application value to it (used when settings are
 * read, so a platform that tightens later still reaches every application), and the returned list names what was
 * raised (used to refuse a weaker save with a clear message).
 */
public final class PlatformMinimums {

    private PlatformMinimums() {}

    public record Result(Map<String, Object> value, List<String> raised) {
        public void requireNothingRaised() {
            if (!raised.isEmpty()) {
                throw new IllegalArgumentException(
                        "Applications can be stricter than the platform, not weaker: " + String.join("; ", raised) + ".");
            }
        }
    }

    public static Result password(Map<String, Object> platform, Map<String, Object> app) {
        Floor f = new Floor(platform, app);
        f.atLeast("minLength", "Minimum length must be at least %s");
        f.required("requireUppercase", "An upper-case letter must be required");
        f.required("requireNumber", "A number must be required");
        f.required("requireSymbol", "A symbol must be required");
        f.required("breachCheck", "Leaked passwords must be blocked");
        f.atLeast("historyCount", "Password history must be at least %s");
        f.atMost("maxFailedAttempts", "Lock after at most %s failed sign-ins");
        f.atLeast("lockoutDurationMinutes", "Lockout must last at least %s minutes");
        int platformExpiry = intOf(platform.get("expiryDays"), 0);
        int appExpiry = intOf(f.out.get("expiryDays"), 0);
        if (platformExpiry > 0 && (appExpiry == 0 || appExpiry > platformExpiry)) {
            f.out.put("expiryDays", platformExpiry);
            f.raised.add("Passwords must expire within " + platformExpiry + " days");
        }
        return f.result();
    }

    public static Result token(Map<String, Object> platform, Map<String, Object> app) {
        Floor f = new Floor(platform, app);
        f.atMost("accessTokenTtlSeconds", "Access tokens can last at most %s seconds");
        f.atMost("refreshTokenTtlSeconds", "Refresh tokens can last at most %s seconds");
        f.atMost("authorizationCodeTtlSeconds", "Authorization codes can last at most %s seconds");
        f.atMost("idTokenTtlSeconds", "ID tokens can last at most %s seconds");
        f.atMost("clientCredentialsTtlSeconds", "Client credentials tokens can last at most %s seconds");
        f.atMost("deviceCodeTtlSeconds", "Device codes can last at most %s seconds");
        f.forbidden("refreshTokensEnabled", "Refresh tokens are off on the platform");
        f.forbidden("reuseRefreshTokens", "Refresh tokens cannot be reused on the platform");
        f.required("rotateRefreshTokens", "Refresh tokens must rotate");
        f.required("refreshTokenReuseDetection", "Refresh token reuse detection must stay on");
        f.atMost("refreshTokenAbsoluteSeconds", "A sign-in can be kept by refreshing for at most %s seconds");
        int platformMaxAge = intOf(platform != null ? platform.get("signInMaxAgeSeconds") : null, 0);
        int appMaxAge = intOf(f.out.get("signInMaxAgeSeconds"), 0);
        if (platformMaxAge > 0 && (appMaxAge == 0 || appMaxAge > platformMaxAge)) {
            f.out.put("signInMaxAgeSeconds", platformMaxAge);
            f.raised.add("People must sign in again within " + platformMaxAge + " seconds");
        }
        return f.result();
    }

    @SuppressWarnings("unchecked")
    public static Result verification(Map<String, Object> platform, Map<String, Object> app) {
        Map<String, Object> out = deepCopy(app);
        List<String> raised = new ArrayList<>();
        for (String section : List.of("signup", "login", "recovery")) {
            Map<String, Object> p = platform.get(section) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            Map<String, Object> a = out.get(section) instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
            if (a == null) {
                if (!p.isEmpty()) {
                    out.put(section, deepCopy(p));
                }
                continue;
            }
            String label = switch (section) {
                case "signup" -> "Sign-up: ";
                case "login" -> "Sign-in: ";
                default -> "Recovery: ";
            };
            Floor f = new Floor(p, a);
            f.atLeast("otpLength", label + "codes need at least %s digits");
            f.atMost("otpTtlSeconds", label + "codes can last at most %s seconds");
            f.atMost("maxAttempts", label + "at most %s attempts per code");
            f.atLeast("resendCooldownSeconds", label + "wait at least %s seconds before resending");
            switch (section) {
                case "signup" -> f.superset("requiredChannels", label + "must verify %s");
                case "login" -> f.subset("passwordlessModes", label + "passwordless modes are limited to %s", false);
                default -> {
                    f.subset("allowedModes", label + "reset is limited to %s", true);
                    f.subset("allowedChannels", label + "channels are limited to %s", true);
                    f.required("requireVerifiedChannel", label + "a verified channel must be required");
                    f.oneOf("passwordResetMode", "allowedModes");
                }
            }
            out.put(section, f.out);
            raised.addAll(f.raised);
        }
        return new Result(out, raised);
    }

    /** One settings map checked against the platform's. */
    private static final class Floor {
        final Map<String, Object> platform;
        final Map<String, Object> out;
        final List<String> raised = new ArrayList<>();

        Floor(Map<String, Object> platform, Map<String, Object> app) {
            this.platform = platform != null ? platform : Map.of();
            this.out = new LinkedHashMap<>(app != null ? app : Map.of());
        }

        void atLeast(String key, String message) {
            Integer floor = intOrNull(platform.get(key));
            if (floor != null && intOf(out.get(key), floor) < floor) {
                out.put(key, floor);
                raised.add(message.formatted(floor));
            }
        }

        void atMost(String key, String message) {
            Integer ceiling = intOrNull(platform.get(key));
            if (ceiling != null && intOf(out.get(key), ceiling) > ceiling) {
                out.put(key, ceiling);
                raised.add(message.formatted(ceiling));
            }
        }

        /** On at the platform: on here too. */
        void required(String key, String message) {
            if (Boolean.TRUE.equals(platform.get(key)) && !Boolean.TRUE.equals(out.get(key))) {
                out.put(key, true);
                raised.add(message);
            }
        }

        /** Off at the platform: off here too. */
        void forbidden(String key, String message) {
            if (Boolean.FALSE.equals(platform.get(key)) && Boolean.TRUE.equals(out.get(key))) {
                out.put(key, false);
                raised.add(message);
            }
        }

        /** Only what the platform allows; when nothing is left and one is needed, the platform's list. */
        void subset(String key, String message, boolean needsOne) {
            List<String> allowed = strings(platform.get(key));
            if (allowed == null) {
                return;
            }
            List<String> mine = strings(out.get(key));
            if (mine == null) {
                return;
            }
            List<String> kept = mine.stream().filter(allowed::contains).toList();
            if (kept.size() != mine.size()) {
                out.put(key, kept.isEmpty() && needsOne ? allowed : kept);
                raised.add(message.formatted(allowed.isEmpty() ? "none" : String.join(", ", allowed)));
            }
        }

        /** Everything the platform requires, plus anything more. */
        void superset(String key, String message) {
            List<String> required = strings(platform.get(key));
            if (required == null || required.isEmpty()) {
                return;
            }
            List<String> mine = strings(out.get(key));
            List<String> merged = new ArrayList<>(mine != null ? mine : List.of());
            List<String> missing = required.stream().filter(r -> !merged.contains(r)).toList();
            if (!missing.isEmpty()) {
                merged.addAll(missing);
                out.put(key, merged);
                raised.add(message.formatted(String.join(", ", missing)));
            }
        }

        /** Keeps a default choice inside the allowed list (not a weakening, so nothing is reported). */
        void oneOf(String key, String listKey) {
            List<String> allowed = strings(out.get(listKey));
            Object current = out.get(key);
            if (allowed != null && !allowed.isEmpty() && current != null && !allowed.contains(current.toString())) {
                out.put(key, allowed.get(0));
            }
        }

        Result result() {
            return new Result(out, raised);
        }
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static Integer intOrNull(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static int intOf(Object raw, int fallback) {
        Integer value = intOrNull(raw);
        return value != null ? value : fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((k, v) -> copy.put(k, v instanceof Map<?, ?> m ? deepCopy((Map<String, Object>) m) : v));
        return copy;
    }
}
