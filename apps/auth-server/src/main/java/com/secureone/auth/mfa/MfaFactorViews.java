package com.secureone.auth.mfa;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MfaFactorViews {

    private MfaFactorViews() {}

    public static Map<String, Object> toPublicView(MfaFactor factor) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", factor.getId().toString());
        out.put("type", factor.getType());
        out.put("label", factor.getLabel() != null ? factor.getLabel() : factor.getType());
        out.put("verified", factor.isVerified());
        out.put("createdAt", factor.getCreatedAt());
        Instant lastUsed = factor.getLastUsedAt();
        if (lastUsed != null) {
            out.put("lastUsedAt", lastUsed);
        }
        return out;
    }

    public static List<Map<String, Object>> listForUser(MfaFactorRepository factors, java.util.UUID userId) {
        return factors.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(MfaFactorViews::toPublicView)
                .toList();
    }
}
