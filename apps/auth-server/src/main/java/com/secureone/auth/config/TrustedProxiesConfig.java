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
        String[] parts = entry.split("/");
        String[] octets = parts[0].split("\\.");
        int bits;
        try {
            bits = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            bits = -1;
        }
        if (octets.length != 4 || !(bits == 8 || bits == 16 || bits == 24 || bits == 32)) {
            throw new IllegalArgumentException(
                    "SECUREONE_TRUSTED_PROXIES: use an address or an IPv4 range ending in /8, /16, /24 or /32: " + entry);
        }
        int keep = bits / 8;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            out.add(i < keep ? octets[i] : ipv4);
        }
        return String.join("\\.", out);
    }
}
