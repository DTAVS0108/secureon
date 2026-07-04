package com.secureone.auth.serviceaccount;

import com.secureone.auth.admin.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ServiceAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServiceAccountRepository accounts;
    private final ApiKeyRepository apiKeys;

    public ServiceAccountService(ServiceAccountRepository accounts, ApiKeyRepository apiKeys) {
        this.accounts = accounts;
        this.apiKeys = apiKeys;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return accounts.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, UUID id) {
        return toResponse(requireAccount(tenantId, id));
    }

    public Map<String, Object> create(UUID tenantId, Map<String, Object> body) {
        String name = requiredString(body.get("name"), "name");
        accounts.findByTenantIdAndName(tenantId, name)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Service account name already exists.");
                });
        ServiceAccount account = new ServiceAccount();
        account.setTenantId(tenantId);
        account.setName(name);
        account.setDescription(string(body.get("description")));
        account.setStatus(normalizeStatus(body.get("status")));
        return toResponse(accounts.save(account));
    }

    public Map<String, Object> update(UUID tenantId, UUID id, Map<String, Object> body) {
        ServiceAccount account = requireAccount(tenantId, id);
        if (body.containsKey("name")) {
            String name = requiredString(body.get("name"), "name");
            accounts.findByTenantIdAndName(tenantId, name)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Service account name already exists.");
                    });
            account.setName(name);
        }
        if (body.containsKey("description")) {
            account.setDescription(string(body.get("description")));
        }
        if (body.containsKey("status")) {
            account.setStatus(normalizeStatus(body.get("status")));
        }
        return toResponse(accounts.save(account));
    }

    public void delete(UUID tenantId, UUID id) {
        ServiceAccount account = requireAccount(tenantId, id);
        accounts.delete(account);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listKeys(UUID tenantId, UUID serviceAccountId) {
        requireAccount(tenantId, serviceAccountId);
        return apiKeys.findByServiceAccountIdOrderByCreatedAtDesc(serviceAccountId).stream()
                .map(this::toKeyResponse)
                .toList();
    }

    public Map<String, Object> createApiKey(UUID tenantId, UUID serviceAccountId, Map<String, Object> body) {
        requireAccount(tenantId, serviceAccountId);
        String rawKey = generateRawKey();
        String prefix = rawKey.substring(0, Math.min(16, rawKey.length()));
        ApiKey key = new ApiKey();
        key.setServiceAccountId(serviceAccountId);
        key.setKeyPrefix(prefix);
        key.setKeyHash(hashKey(rawKey));
        key.setLabel(string(body != null ? body.get("label") : null));
        Object expiresAt = body != null ? body.get("expiresAt") : null;
        if (expiresAt != null && !expiresAt.toString().isBlank()) {
            key.setExpiresAt(Instant.parse(expiresAt.toString()));
        }
        key = apiKeys.save(key);
        Map<String, Object> out = toKeyResponse(key);
        out.put("apiKey", rawKey);
        return out;
    }

    public void revokeKey(UUID tenantId, UUID serviceAccountId, UUID keyId) {
        requireAccount(tenantId, serviceAccountId);
        ApiKey key = apiKeys.findById(keyId)
                .filter(row -> serviceAccountId.equals(row.getServiceAccountId()))
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + keyId));
        if (key.getRevokedAt() == null) {
            key.setRevokedAt(Instant.now());
            apiKeys.save(key);
        }
    }

    @Transactional
    public Optional<ServiceAccountContext> validateApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || !rawKey.startsWith("sk_live_")) {
            return Optional.empty();
        }
        return apiKeys.findByKeyHash(hashKey(rawKey))
                .filter(ApiKey::isActive)
                .flatMap(key -> accounts.findById(key.getServiceAccountId())
                        .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                        .map(account -> {
                            key.setLastUsedAt(Instant.now());
                            apiKeys.save(key);
                            return new ServiceAccountContext(account.getTenantId(), account.getId(), account.getName());
                        }));
    }

    private ServiceAccount requireAccount(UUID tenantId, UUID id) {
        return accounts.findById(id)
                .filter(row -> tenantId.equals(row.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Service account not found: " + id));
    }

    private Map<String, Object> toResponse(ServiceAccount account) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", account.getId().toString());
        out.put("tenantId", account.getTenantId().toString());
        out.put("name", account.getName());
        out.put("description", account.getDescription());
        out.put("status", account.getStatus());
        out.put("createdAt", account.getCreatedAt().toString());
        out.put("updatedAt", account.getUpdatedAt().toString());
        return out;
    }

    private Map<String, Object> toKeyResponse(ApiKey key) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", key.getId().toString());
        out.put("serviceAccountId", key.getServiceAccountId().toString());
        out.put("keyPrefix", key.getKeyPrefix());
        out.put("label", key.getLabel());
        out.put("createdAt", key.getCreatedAt().toString());
        out.put("expiresAt", key.getExpiresAt() != null ? key.getExpiresAt().toString() : null);
        out.put("lastUsedAt", key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : null);
        out.put("revokedAt", key.getRevokedAt() != null ? key.getRevokedAt().toString() : null);
        out.put("active", key.isActive());
        return out;
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String generateRawKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sk_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requiredString(Object raw, String field) {
        String value = string(raw);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString().trim() : null;
    }

    private static String normalizeStatus(Object raw) {
        if (raw == null) {
            return "ACTIVE";
        }
        String status = raw.toString().trim().toUpperCase(Locale.ROOT);
        return "DISABLED".equals(status) ? "DISABLED" : "ACTIVE";
    }
}
