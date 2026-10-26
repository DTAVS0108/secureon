package com.secureone.auth.application;

import com.secureone.auth.user.UserAccount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An application's own token claims (Integration › Token claims), like Auth0 Actions or Okta claim mappings: each
 * adds a claim to the application's access and/or ID tokens, from a user attribute, a fixed value or a profile
 * field. Names that tokens already use (sub, roles, email…) cannot be taken over.
 */
public final class TokenClaimMappings {

    private TokenClaimMappings() {}

    public static final String SETTING_KEY = "token_claims";
    static final int MAX_CLAIMS = 20;

    /** Where a claim's value comes from. */
    public static final Set<String> SOURCES = Set.of(
            "attribute", "static", "email", "email_verified", "phone", "phone_verified", "display_name", "username",
            "tenant_slug");

    /**
     * User attributes SecureOne keeps for itself (a pending email change, a pending invite, sign-in method choices):
     * never put in tokens. Kept in step with EmailChangeService, UserInviteContext and UserAuthPreferences.
     */
    static final Set<String> INTERNAL_ATTRIBUTES = Set.of(
            "pendingEmail", "pendingEmailApplicationId", "pendingInviteApplicationId", "authMethods");

    /** Claims SecureOne or the standards already set. */
    static final Set<String> RESERVED = Set.of(
            "iss", "sub", "aud", "exp", "nbf", "iat", "jti", "azp", "nonce", "auth_time", "acr", "amr", "sid", "at_hash",
            "c_hash", "scope", "scp", "client_id", "cnf", "act", "may_act", "events", "user_id", "tenant_id", "roles",
            "permissions", "email", "email_verified", "name", "preferred_username", "given_name", "family_name",
            "phone_number", "phone_number_verified");

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.:/\\-]{1,100}$");

    public record Claim(String name, String source, String value, boolean accessToken, boolean idToken) {}

    /** Validates a saved list; throws with the first problem. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> sanitize(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        if (list.size() > MAX_CLAIMS) {
            throw new IllegalArgumentException("At most " + MAX_CLAIMS + " token claims.");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> in = (Map<String, Object>) m;
            String name = str(in.get("name")).trim();
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Claim name \"" + name + "\" is not valid (letters, digits, _ . : / -).");
            }
            if (RESERVED.contains(name)) {
                throw new IllegalArgumentException("\"" + name + "\" is already set by SecureOne; choose another name (for example with your domain: https://example.com/" + name + ").");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Claim \"" + name + "\" is listed twice.");
            }
            String source = str(in.get("source"));
            if (!SOURCES.contains(source)) {
                throw new IllegalArgumentException("Claim \"" + name + "\": unknown source " + source + ".");
            }
            String value = str(in.get("value")).trim();
            if (("attribute".equals(source) || "static".equals(source)) && value.isEmpty()) {
                throw new IllegalArgumentException("Claim \"" + name + "\" needs a " + ("static".equals(source) ? "value" : "user attribute name") + ".");
            }
            if ("attribute".equals(source) && INTERNAL_ATTRIBUTES.contains(value)) {
                throw new IllegalArgumentException("Claim \"" + name + "\": " + value + " is kept by SecureOne for its own use and cannot go in tokens.");
            }
            if (value.length() > 500) {
                throw new IllegalArgumentException("Claim \"" + name + "\": the value is too long.");
            }
            boolean access = !Boolean.FALSE.equals(in.get("accessToken"));
            boolean id = Boolean.TRUE.equals(in.get("idToken"));
            if (!access && !id) {
                throw new IllegalArgumentException("Claim \"" + name + "\" must go in the access token, the ID token or both.");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("source", source);
            row.put("value", value);
            row.put("accessToken", access);
            row.put("idToken", id);
            out.add(row);
        }
        return out;
    }

    public static List<Claim> parse(Object raw) {
        List<Claim> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new Claim(str(m.get("name")), str(m.get("source")), str(m.get("value")),
                            !Boolean.FALSE.equals(m.get("accessToken")), Boolean.TRUE.equals(m.get("idToken"))));
                }
            }
        }
        return out;
    }

    /** The claim's value for this user (null: leave the claim out). With no user, only fixed values apply. */
    public static Object valueFor(Claim claim, UserAccount user, String tenantSlug) {
        if ("static".equals(claim.source())) {
            return claim.value();
        }
        if (user == null) {
            return null;
        }
        return switch (claim.source()) {
            case "attribute" -> user.getAttributes() != null && !INTERNAL_ATTRIBUTES.contains(claim.value())
                    ? user.getAttributes().get(claim.value())
                    : null;
            case "email" -> user.getEmail();
            case "email_verified" -> user.isEmailVerified();
            case "phone" -> user.getPhone();
            case "phone_verified" -> user.isPhoneVerified();
            case "display_name" -> user.getDisplayName();
            case "username" -> user.getUsername();
            case "tenant_slug" -> tenantSlug;
            default -> null;
        };
    }

    private static String str(Object value) {
        return value != null ? value.toString() : "";
    }
}
