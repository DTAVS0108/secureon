package com.secureone.auth.account;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Have I Been Pwned k-anonymity range API — password is never sent to the service. */
@Service
public class LeakedPasswordService {

    private static final Logger log = LoggerFactory.getLogger(LeakedPasswordService.class);
    private static final String RANGE_URL = "https://api.pwnedpasswords.com/range/";

    private final RestClient http;
    private final SecureOneEnvironmentProperties environment;
    private final boolean globallyEnabled;

    @Autowired
    public LeakedPasswordService(
            SecureOneEnvironmentProperties environment,
            @Value("${secureone.password.breach-check-enabled:true}") boolean globallyEnabled) {
        this.environment = environment;
        this.globallyEnabled = globallyEnabled;
        this.http = RestClient.builder()
                .defaultHeader("User-Agent", "SecureOne-AuthServer")
                .build();
    }

    LeakedPasswordService(
            SecureOneEnvironmentProperties environment, boolean globallyEnabled, RestClient http) {
        this.environment = environment;
        this.globallyEnabled = globallyEnabled;
        this.http = http;
    }

    public void assertNotLeaked(String password, boolean policyEnabled) {
        if (!globallyEnabled || !policyEnabled || password == null || password.isBlank()) {
            return;
        }
        if (isLeaked(password)) {
            throw new IllegalArgumentException(
                    "This password has appeared in a known data breach. Choose a different password.");
        }
    }

    boolean isLeaked(String password) {
        String hash = sha1Hex(password);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);
        try {
            String body = http.get()
                    .uri(RANGE_URL + prefix)
                    .retrieve()
                    .body(String.class);
            return body != null && containsSuffix(body, suffix);
        } catch (RestClientException ex) {
            if (environment.isProduction()) {
                log.error("Breach check unavailable in production — rejecting password set: {}", ex.getMessage());
                throw new IllegalStateException(
                        "Password breach check is temporarily unavailable. Try again shortly.");
            }
            log.warn("Breach check skipped (API unavailable): {}", ex.getMessage());
            return false;
        }
    }

    static String sha1Hex(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format(Locale.ROOT, "%02X", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }

    static boolean containsSuffix(String body, String suffix) {
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String lineSuffix = colon > 0 ? trimmed.substring(0, colon) : trimmed;
            if (suffix.equalsIgnoreCase(lineSuffix.trim())) {
                return true;
            }
        }
        return false;
    }
}
