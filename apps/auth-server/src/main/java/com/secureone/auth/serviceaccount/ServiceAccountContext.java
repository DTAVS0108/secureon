package com.secureone.auth.serviceaccount;

import java.util.UUID;

public record ServiceAccountContext(UUID tenantId, UUID serviceAccountId, UUID apiKeyId, String name) {

    /** Scopes granted to M2M API keys for this tenant service account. */
    public static java.util.List<String> defaultScopes() {
        return java.util.List.of("m2m:read", "m2m:tenant", "m2m:health");
    }
}
