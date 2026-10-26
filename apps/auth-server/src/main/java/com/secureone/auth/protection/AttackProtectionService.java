package com.secureone.auth.protection;

import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.PlatformSettingsService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Attack protection (Platform settings › Attack protection), like Auth0's or Okta's:
 * <ul>
 *   <li>Bot protection: a CAPTCHA (Cloudflare Turnstile, hCaptcha or Google reCAPTCHA) on sign-up, password
 *       reset, magic links and one-time codes, and on password sign-in always or after repeated failures from one
 *       address.</li>
 *   <li>IP rules: addresses that are refused everywhere, and, optionally, the only addresses the admin console
 *       accepts.</li>
 * </ul>
 */
@Service
public class AttackProtectionService {

    private static final Logger log = LoggerFactory.getLogger(AttackProtectionService.class);

    public static final String SETTINGS_KEY = "attack_protection";
    public static final Set<String> PROVIDERS = Set.of("none", "turnstile", "hcaptcha", "recaptcha");
    public static final Set<String> SIGN_IN_MODES = Set.of("never", "after_failures", "always");
    /** What a CAPTCHA can protect (besides password sign-in, which has its own mode). */
    public static final List<String> ACTIONS = List.of("signUp", "passwordReset", "magicLink", "oneTimeCodes");

    private static final Map<String, String> VERIFY_URLS = Map.of(
            "turnstile", "https://challenges.cloudflare.com/turnstile/v0/siteverify",
            "hcaptcha", "https://api.hcaptcha.com/siteverify",
            "recaptcha", "https://www.google.com/recaptcha/api/siteverify");
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final PlatformSettingsService settings;
    private final SecretProtector secrets;
    private final StringRedisTemplate redis;
    private final RestClient http;
    /** The providers' check addresses (tests point them at an address that does not answer). */
    Map<String, String> verifyUrls = VERIFY_URLS;

    public AttackProtectionService(PlatformSettingsService settings, SecretProtector secrets, StringRedisTemplate redis) {
        this.settings = settings;
        this.secrets = secrets;
        this.redis = redis;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> bot = new LinkedHashMap<>();
        bot.put("provider", "none");
        bot.put("siteKey", "");
        ACTIONS.forEach(a -> bot.put(a, true));
        bot.put("signIn", "after_failures");
        bot.put("signInFailures", 3);
        bot.put("allowedHostnames", List.of());
        bot.put("failOpen", false);
        Map<String, Object> ip = new LinkedHashMap<>();
        ip.put("blocked", List.of());
        ip.put("consoleAllowed", List.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("botProtection", bot);
        out.put("ipRules", ip);
        out.put("newDeviceAlerts", true);
        return out;
    }

    /** Email users about sign-ins from a browser and system their account has not used before. */
    public boolean newDeviceAlerts() {
        return !Boolean.FALSE.equals(stored().get("newDeviceAlerts"));
    }

    // ---- Settings ------------------------------------------------------------------------------------------------

    /** For the console: everything except the secret, which is reported only as set or not. */
    public Map<String, Object> getForAdmin() {
        Map<String, Object> stored = stored();
        Map<String, Object> out = sanitize(stored, stored);
        @SuppressWarnings("unchecked")
        Map<String, Object> bot = (Map<String, Object>) out.get("botProtection");
        bot.put("secretSet", !string(botOf(stored).get("secretEncrypted")).isBlank());
        bot.remove("secretEncrypted");
        return out;
    }

    /** A blank or missing {@code secret} keeps the stored one. */
    public Map<String, Object> save(Map<String, Object> body) {
        Map<String, Object> stored = stored();
        Map<String, Object> next = sanitize(body, stored);
        @SuppressWarnings("unchecked")
        Map<String, Object> bot = (Map<String, Object>) next.get("botProtection");
        Object incoming = body.get("botProtection") instanceof Map<?, ?> b ? b.get("secret") : null;
        if (incoming instanceof String s && !s.isBlank()) {
            bot.put("secretEncrypted", secrets.encrypt(s.trim()));
        }
        if (!"none".equals(bot.get("provider"))) {
            if (string(bot.get("siteKey")).isBlank() || string(bot.get("secretEncrypted")).isBlank()) {
                throw new IllegalArgumentException("Bot protection needs the provider's site key and secret key.");
            }
        }
        settings.saveMap(SETTINGS_KEY, next);
        cached = null;
        return getForAdmin();
    }

    /** What the hosted pages need to show the CAPTCHA (no secret). */
    public Map<String, Object> publicConfig() {
        Map<String, Object> bot = botOf(sanitize(stored(), stored()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", bot.get("provider"));
        out.put("siteKey", bot.get("siteKey"));
        ACTIONS.forEach(a -> out.put(a, bot.get(a)));
        out.put("signIn", bot.get("signIn"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitize(Map<String, Object> body, Map<String, Object> stored) {
        Map<String, Object> d = defaults();
        Map<String, Object> inBot = body.get("botProtection") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> bot = new LinkedHashMap<>((Map<String, Object>) d.get("botProtection"));
        String provider = string(inBot.get("provider")).toLowerCase(Locale.ROOT);
        bot.put("provider", PROVIDERS.contains(provider) ? provider : "none");
        bot.put("siteKey", string(inBot.get("siteKey")).trim());
        for (String a : ACTIONS) {
            if (inBot.get(a) instanceof Boolean b) {
                bot.put(a, b);
            }
        }
        String signIn = string(inBot.get("signIn"));
        if (SIGN_IN_MODES.contains(signIn)) {
            bot.put("signIn", signIn);
        }
        if (inBot.get("signInFailures") instanceof Number n) {
            bot.put("signInFailures", Math.max(1, Math.min(20, n.intValue())));
        }
        bot.put("allowedHostnames", hostnames(inBot.get("allowedHostnames")));
        bot.put("failOpen", Boolean.TRUE.equals(inBot.get("failOpen")));
        String storedSecret = string(botOf(stored).get("secretEncrypted"));
        if (!storedSecret.isBlank()) {
            bot.put("secretEncrypted", storedSecret);
        }

        Map<String, Object> inIp = body.get("ipRules") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> ip = new LinkedHashMap<>();
        ip.put("blocked", ranges(inIp.get("blocked")));
        ip.put("consoleAllowed", ranges(inIp.get("consoleAllowed")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("botProtection", bot);
        out.put("ipRules", ip);
        out.put("newDeviceAlerts", !Boolean.FALSE.equals(body.get("newDeviceAlerts")));
        return out;
    }

    /** Host names the CAPTCHA may be solved on ("login.example.com"); empty accepts any. */
    private static List<String> hostnames(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String s = string(o).trim().toLowerCase(Locale.ROOT);
                if (s.isEmpty() || out.contains(s)) {
                    continue;
                }
                if (!s.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*") || s.length() > 253) {
                    throw new IllegalArgumentException("Not a host name: " + s);
                }
                out.add(s);
            }
            if (out.size() > 20) {
                throw new IllegalArgumentException("At most 20 CAPTCHA host names.");
            }
        }
        return out;
    }

    /** Addresses or CIDR ranges ("203.0.113.7", "10.0.0.0/8", "2001:db8::/32"); a bad entry is refused. */
    private static List<String> ranges(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String s = string(o).trim();
                if (s.isEmpty() || out.contains(s)) {
                    continue;
                }
                try {
                    new IpAddressMatcher(s).matches("127.0.0.1");
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Not an IP address or range: " + s);
                }
                out.add(s);
            }
        }
        return out;
    }

    /** Read on every request by the filter: kept for a few seconds (other servers see a change within that time). */
    private static final long CACHE_MILLIS = 5_000;
    private volatile Map<String, Object> cached;
    private volatile long cachedAt;

    private Map<String, Object> stored() {
        Map<String, Object> c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            return c;
        }
        Map<String, Object> s = com.secureone.auth.tenant.RlsScope.asPlatform(() -> settings.get(SETTINGS_KEY));
        c = s != null && !s.isEmpty() ? s : defaults();
        cached = c;
        cachedAt = System.currentTimeMillis();
        return c;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> botOf(Map<String, Object> config) {
        return config.get("botProtection") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> ipList(String key) {
        Map<String, Object> ip = stored().get("ipRules") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return ip.get(key) instanceof List<?> l ? (List<String>) l : List.of();
    }

    // ---- IP rules ------------------------------------------------------------------------------------------------

    public boolean isBlocked(String ip) {
        return ip != null && matchesAny(ip, ipList("blocked"));
    }

    /** True when the console may be used from {@code ip} (no allow-list set, this machine, or a listed address). */
    public boolean consoleAllowed(String ip) {
        List<String> allowed = ipList("consoleAllowed");
        if (allowed.isEmpty() || ip == null) {
            return true;
        }
        // The console's own server talks to this one over loopback.
        return isLoopback(ip) || matchesAny(ip, allowed);
    }

    private static boolean matchesAny(String ip, List<String> ranges) {
        for (String range : ranges) {
            try {
                if (new IpAddressMatcher(range).matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // A bad stored entry never blocks anyone.
            }
        }
        return false;
    }

    private static boolean isLoopback(String ip) {
        return ip.startsWith("127.") || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    // ---- Bot protection ------------------------------------------------------------------------------------------

    public boolean enabled() {
        return !"none".equals(botOf(stored()).getOrDefault("provider", "none"));
    }

    /** Whether {@code action} (one of {@link #ACTIONS}) needs a CAPTCHA. */
    public boolean required(String action) {
        Map<String, Object> bot = botOf(stored());
        return enabled() && Boolean.TRUE.equals(bot.getOrDefault(action, true));
    }

    /** Whether a password sign-in from {@code ip} needs a CAPTCHA now. */
    public boolean signInRequired(String ip) {
        if (!enabled()) {
            return false;
        }
        Map<String, Object> bot = botOf(stored());
        String mode = string(bot.getOrDefault("signIn", "after_failures"));
        if ("always".equals(mode)) {
            return true;
        }
        if (!"after_failures".equals(mode) || ip == null) {
            return false;
        }
        int threshold = bot.get("signInFailures") instanceof Number n ? n.intValue() : 3;
        String value = redis.opsForValue().get(failureKey(ip));
        return value != null && Integer.parseInt(value) >= threshold;
    }

    public void recordSignInFailure(String ip) {
        if (ip == null) {
            return;
        }
        String key = failureKey(ip);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, FAILURE_WINDOW);
        }
    }

    private static String failureKey(String ip) {
        return "secureone:signin-failures:" + ip;
    }

    /**
     * Asks the provider whether {@code token} is a solved challenge, on one of the allowed host names when any are
     * set. If the provider cannot be reached the request is refused, unless fail open is on.
     */
    public boolean verify(String token, String ip) {
        if (!enabled()) {
            return true;
        }
        if (token == null || token.isBlank() || token.length() > 4096) {
            return false;
        }
        Map<String, Object> bot = botOf(stored());
        String provider = string(bot.get("provider"));
        String secret;
        try {
            secret = secrets.decrypt(string(bot.get("secretEncrypted")));
        } catch (RuntimeException e) {
            log.error("Bot protection: the secret key cannot be read; challenges are refused until it is saved again.");
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (ip != null) {
            form.add("remoteip", ip);
        }
        try {
            Map<?, ?> result = http.post()
                    .uri(verifyUrls.get(provider))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
                return false;
            }
            // A token solved on someone else's site (with this site key copied) is refused when hostnames are set.
            List<String> hostnames = stringList(bot.get("allowedHostnames"));
            if (!hostnames.isEmpty() && !hostnames.contains(string(result.get("hostname")).toLowerCase(Locale.ROOT))) {
                log.warn("Bot protection: a challenge solved on {} was refused (not an allowed hostname).",
                        result.get("hostname"));
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            if (Boolean.TRUE.equals(bot.get("failOpen"))) {
                log.warn("Bot protection: {} could not be reached ({}); let through (fail open is on).", provider,
                        e.getMessage());
                return true;
            }
            log.warn("Bot protection: {} could not be reached ({}); the request is refused.", provider, e.getMessage());
            return false;
        }
    }

    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(o -> out.add(string(o)));
        }
        return out;
    }

    private static String string(Object value) {
        return value != null ? value.toString() : "";
    }
}
