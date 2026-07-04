package com.secureone.auth.serviceaccount;

import java.util.UUID;

public record ServiceAccountContext(UUID tenantId, UUID serviceAccountId, String name) {}
