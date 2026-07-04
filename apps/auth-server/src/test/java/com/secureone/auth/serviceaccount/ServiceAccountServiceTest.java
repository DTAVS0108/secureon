package com.secureone.auth.serviceaccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceAccountServiceTest {

    @Mock
    private ServiceAccountRepository accounts;

    @Mock
    private ApiKeyRepository apiKeys;

    private ServiceAccountService service;

    @BeforeEach
    void setUp() {
        service = new ServiceAccountService(accounts, apiKeys);
    }

    @Test
    void createApiKeyReturnsRawKeyOnce() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceAccountId = UUID.randomUUID();
        ServiceAccount account = new ServiceAccount();
        account.setId(serviceAccountId);
        account.setTenantId(tenantId);
        account.setName("ci-bot");
        when(accounts.findById(serviceAccountId)).thenReturn(Optional.of(account));
        when(apiKeys.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            if (key.getCreatedAt() == null) {
                key.setCreatedAt(java.time.Instant.now());
            }
            return key;
        });

        Map<String, Object> created = service.createApiKey(tenantId, serviceAccountId, Map.of("label", "deploy"));

        assertNotNull(created.get("apiKey"));
        assertTrue(created.get("apiKey").toString().startsWith("sk_live_"));
        assertFalse(created.containsKey("keyHash"));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeys).save(captor.capture());
        assertEquals(
                ServiceAccountService.hashKey(created.get("apiKey").toString()),
                captor.getValue().getKeyHash());
    }

    @Test
    void hashKeyIsDeterministic() {
        String hash = ServiceAccountService.hashKey("sk_live_test");
        assertEquals(64, hash.length());
        assertEquals(hash, ServiceAccountService.hashKey("sk_live_test"));
    }
}
