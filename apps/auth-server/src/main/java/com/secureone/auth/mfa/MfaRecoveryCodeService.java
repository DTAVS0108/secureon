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

    private final MfaFactorRepository factors;
    private final PasswordEncoder passwordEncoder;

    public MfaRecoveryCodeService(MfaFactorRepository factors, PasswordEncoder passwordEncoder) {
        this.factors = factors;
        this.passwordEncoder = passwordEncoder;
    }

    public List<String> issueForUser(UUID userId, int count) {
        revokeForUser(userId);
        List<String> plainCodes = new ArrayList<>();
        List<Map<String, Object>> stored = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = generateCode();
            plainCodes.add(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hash", passwordEncoder.encode(code));
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
                if (hash != null && passwordEncoder.matches(code, hash.toString())) {
                    row.put("used", true);
                    factor.setLastUsedAt(java.time.Instant.now());
                    factors.save(factor);
                    return true;
                }
            }
        }
        return false;
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
