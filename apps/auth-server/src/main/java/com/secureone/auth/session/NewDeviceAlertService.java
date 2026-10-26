package com.secureone.auth.session;

import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.protection.AttackProtectionService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccount;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * "New sign-in to your account" emails: when someone signs in from a browser and system, or a network, this account
 * has not used in the last 180 days, the user is told (browser, system, address), so a stolen password is noticed.
 * Not on the very first sign-in. On by default; Platform settings › Attack protection turns it off.
 */
@Service
public class NewDeviceAlertService {

    private static final Logger log = LoggerFactory.getLogger(NewDeviceAlertService.class);

    private final JdbcTemplate jdbc;
    private final EmailNotificationService mail;
    private final AttackProtectionService protection;
    private final String publicBaseUrl;

    public NewDeviceAlertService(
            JdbcTemplate jdbc,
            EmailNotificationService mail,
            AttackProtectionService protection,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.protection = protection;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * A browser and system, or a network (IPv4 /16, IPv6 /32: roughly one provider's area), not used in 180 days.
     * Call before the new sign-in is recorded.
     */
    public boolean isNewDevice(UUID userId, String userAgent, String ip) {
        List<Map<String, Object>> previous = RlsScope.asPlatform(() -> jdbc.queryForList("""
                SELECT DISTINCT device, ip FROM platform.login_history
                WHERE user_id = ? AND result = 'SUCCESS' AND created_at > now() - interval '180 days'
                LIMIT 500
                """, userId));
        if (previous.isEmpty()) {
            return false; // first sign-in: nothing to compare with
        }
        String device = describe(userAgent);
        if (previous.stream().map(r -> describe((String) r.get("device"))).noneMatch(device::equals)) {
            return true;
        }
        String network = network(ip);
        if (network == null) {
            return false;
        }
        Set<String> known = new HashSet<>();
        previous.forEach(r -> {
            String n = network((String) r.get("ip"));
            if (n != null) {
                known.add(n);
            }
        });
        return !known.isEmpty() && !known.contains(network);
    }

    /** "203.0" for 203.0.113.7, "2001:db8" for 2001:db8::1; null when not an address. Never looks anything up. */
    static String network(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            byte[] b = InetAddress.getByName(ip).getAddress();
            return b.length == 4
                    ? (b[0] & 0xff) + "." + (b[1] & 0xff)
                    : String.format("%x:%x", ((b[0] & 0xff) << 8) | (b[1] & 0xff), ((b[2] & 0xff) << 8) | (b[3] & 0xff));
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Async
    public void alert(UUID applicationId, UserAccount user, String userAgent, String ip) {
        if (!protection.newDeviceAlerts()) {
            return;
        }
        try {
            String link = publicBaseUrl + "/account/security.html" + (applicationId != null ? "?applicationId=" + applicationId : "");
            mail.sendSecurityNotice(applicationId, user,
                    "New sign-in to your account from " + describe(userAgent) + (ip != null ? " (address " + ip + ")" : "")
                            + ". If this was you, there is nothing to do. If not, change your password and sign out other devices.",
                    link);
        } catch (RuntimeException e) {
            log.warn("New-device alert for {} not sent: {}", user.getEmail(), e.getMessage());
        }
    }

    /** "Chrome on Windows": what the email names, and what counts as the same device. */
    static String describe(String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("opr/") || ua.contains("opera") ? "Opera"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("chrome/") || ua.contains("crios/") ? "Chrome"
                : ua.contains("safari/") ? "Safari"
                : ua.isBlank() ? "an unknown browser" : "another app or browser";
        String system = ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("windows") ? "Windows"
                : ua.contains("mac os") || ua.contains("macintosh") ? "macOS"
                : ua.contains("linux") ? "Linux"
                : "an unknown system";
        return browser + " on " + system;
    }
}
