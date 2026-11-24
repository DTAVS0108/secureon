package com.secureone.auth.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Which proxies may tell SecureOne the client's address (X-Forwarded-For) and scheme. Only these: loopback (the
 * admin console's own proxy, always) and the addresses in {@code SECUREONE_TRUSTED_PROXIES}. Everyone else's
 * forwarded headers are ignored, so a client reaching SecureOne directly (for example a published Docker port, where
 * every request comes from the private Docker gateway) cannot choose its own address to get past IP rules, rate
 * limits or the CAPTCHA failure count.
 *
 * <p>{@code SECUREONE_TRUSTED_PROXIES}: comma-separated addresses or IPv4 ranges ending in /8, /16, /24 or /32,
 * for example {@code 10.0.0.5,10.1.0.0/16}. Set it to your reverse proxy or load balancer.
 */
@Configuration
public class TrustedProxiesConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, Ordered {

    static final String LOOPBACK = "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|0:0:0:0:0:0:0:1|::1";

    private final String trustedProxies;

    public TrustedProxiesConfig(@Value("${secureone.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        String pattern = pattern(trustedProxies);
        for (Valve valve : factory.getEngineValves()) {
            if (valve instanceof RemoteIpValve remoteIp) {
                remoteIp.setInternalProxies(pattern);
                remoteIp.setTrustedProxies(null);
            }
        }
    }

    /** Runs after Spring Boot adds the RemoteIpValve (server.forward-headers-strategy=native). */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** The valve's pattern for loopback plus the configured addresses and ranges. */
    static String pattern(String configured) {
        List<String> parts = new ArrayList<>();
        parts.add(LOOPBACK);
        if (configured != null) {
            for (String raw : configured.split(",")) {
                String entry = raw.trim();
                if (!entry.isEmpty()) {
                    parts.add(toRegex(entry));
                }
            }
        }
        String joined = String.join("|", parts);
        Pattern.compile(joined); // fail at start-up rather than trust nothing silently
        return joined;
    }

    static String toRegex(String entry) {
        String ipv4 = "(25[0-5]|2[0-4]\\d|1?\\d?\\d)";
        if (!entry.contains("/")) {
            if (!entry.matches("[0-9A-Fa-f:.]+")) {
                throw new IllegalArgumentException("SECUREONE_TRUSTED_PROXIES: not an address: " + entry);
            }
            return entry.replace(".", "\\.");
        }
        String[] parts = entry.split("/", -1);
        String[] octets = parts[0].split("\\.", -1);
        int bits;
        try {
            bits = parts.length == 2 ? Integer.parseInt(parts[1]) : -1;
        } catch (NumberFormatException e) {
            bits = -1;
        }
        int[] value = new int[4];
        boolean valid = octets.length == 4 && bits >= 0 && bits <= 32;
        for (int i = 0; valid && i < 4; i++) {
            try {
                value[i] = Integer.parseInt(octets[i]);
                valid = octets[i].matches("\\d{1,3}") && value[i] <= 255;
            } catch (NumberFormatException e) {
                valid = false;
            }
        }
        if (!valid) {
            throw new IllegalArgumentException(
                    "SECUREONE_TRUSTED_PROXIES: use an IP address or an IPv4 range such as 10.0.0.0/20: " + entry);
        }
        // Any prefix length: whole octets are fixed, the octet the prefix ends in becomes the list of values it
        // allows, the rest match anything. Host bits set in the given address are ignored (10.0.3.7/20 = 10.0.0.0/20).
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int fixedBits = Math.max(0, Math.min(8, bits - 8 * i));
            if (fixedBits == 8) {
                out.add(Integer.toString(value[i]));
            } else if (fixedBits == 0) {
                out.add(ipv4);
            } else {
                int size = 1 << (8 - fixedBits);
                int start = value[i] & ~(size - 1) & 0xff;
                List<String> allowed = new ArrayList<>();
                for (int v = start; v < start + size; v++) {
                    allowed.add(Integer.toString(v));
                }
                out.add("(" + String.join("|", allowed) + ")");
            }
        }
        return String.join("\\.", out);
    }
}
