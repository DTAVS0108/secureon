package com.secureone.auth.mfa;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MfaRecoveryCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Prefix of codes hashed with HMAC-SHA256 under the server pepper (older ones are password hashes). */
    static final String HMAC_PREFIX = "hmac-sha256:";

    private final MfaFactorRepository factors;
    private final PasswordEncoder passwordEncoder;
    private final com.secureone.auth.account.otp.OtpPepper pepper;

    public MfaRecoveryCodeService(
            MfaFactorRepository factors,
            PasswordEncoder passwordEncoder,
            com.secureone.auth.account.otp.OtpPepper pepper) {
        this.factors = factors;
        this.passwordEncoder = passwordEncoder;
        this.pepper = pepper;
    }

    /**
     * Backup codes are checked against every unused code, so their hash must be fast: HMAC-SHA256 keyed with the
     * server pepper (never readable from the database alone, so a leaked database cannot be searched offline), bound to the user.
     * A slow password hash here (Argon2id) would cost seconds of CPU per guess.
     */
    String hash(UUID userId, String code) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(pepper.value().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((userId + ":" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HMAC_PREFIX + java.util.HexFormat.of().formatHex(out);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    boolean matches(UUID userId, String code, String stored) {
        if (stored.startsWith(HMAC_PREFIX)) {
            return java.security.MessageDigest.isEqual(
                    hash(userId, code).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return passwordEncoder.matches(code, stored); // codes issued before (bcrypt / Argon2id)
    }

    public List<String> issueForUser(UUID userId, int count) {
        revokeForUser(userId);
        List<String> plainCodes = new ArrayList<>();
        List<Map<String, Object>> stored = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = generateCode();
            plainCodes.add(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hash", hash(userId, code));
            row.put("used", false);
            stored.add(row);
        }
        MfaFactor factor = new MfaFactor();
        factor.setUserId(userId);
        factor.setType("recovery");
        factor.setLabel("Recovery codes");
        factor.setVerified(true);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("codes", stored);
        factor.setMetadata(metadata);
        factors.save(factor);
        return plainCodes;
    }

    public boolean verifyAndConsume(UUID userId, String rawCode) {
        String code = normalize(rawCode);
        if (code.isBlank()) {
            return false;
        }
        for (MfaFactor factor : factors.findByUserIdAndType(userId, "recovery")) {
            if (!factor.isVerified()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> codes = (List<Map<String, Object>>) factor.getMetadata().get("codes");
            if (codes == null) {
                continue;
            }
            for (Map<String, Object> row : codes) {
                if (Boolean.TRUE.equals(row.get("used"))) {
                    continue;
                }
                Object hash = row.get("hash");
                if (hash != null && matches(userId, code, hash.toString())) {
                    row.put("used", true);
                    factor.setLastUsedAt(java.time.Instant.now());
                    factors.save(factor);
                    return true;
                }
            }
        }
        return false;
    }

    /** Recovery codes the user can still use. */
    public int remaining(UUID userId) {
        int count = 0;
        for (MfaFactor factor : factors.findByUserIdAndType(userId, "recovery")) {
            Object codes = factor.isVerified() && factor.getMetadata() != null ? factor.getMetadata().get("codes") : null;
            if (codes instanceof List<?> list) {
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map && !Boolean.TRUE.equals(map.get("used"))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void revokeForUser(UUID userId) {
        factors.findByUserIdAndType(userId, "recovery").forEach(factors::delete);
    }

    private static String generateCode() {
        int value = 100_000_000 + RANDOM.nextInt(900_000_000);
        return String.format("%09d", value % 1_000_000_000).substring(0, 9);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").trim().toUpperCase();
    }
}
