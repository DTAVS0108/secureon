package com.secureone.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApplicationApiServiceTest {

    @Test
    void identifiersAreHttpsUrlsOrUrns() {
        for (String ok : List.of("https://api.example.com", "https://api.example.com/orders", "urn:example:orders")) {
            assertNull(ApplicationApiService.identifierProblem(ok), ok);
        }
        for (String bad : List.of("", "orders", "ftp://x.example", "https://api.example.com/#x", "https://a b.example")) {
            assertNotNull(ApplicationApiService.identifierProblem(bad), bad);
        }
    }

    @Test
    void scopesAreCheckedDeduplicatedAndNeverOidcScopes() {
        List<Map<String, String>> scopes = ApplicationApiService.scopes(List.of(
                Map.of("value", "orders:read", "description", "Read orders"), "orders:read", "orders:write", " "));
        assertEquals(List.of("orders:read", "orders:write"), scopes.stream().map(s -> s.get("value")).toList());
        assertEquals("Read orders", scopes.get(0).get("description"));
        assertThrows(ResponseStatusException.class, () -> ApplicationApiService.scopes(List.of("email")));
        assertThrows(ResponseStatusException.class, () -> ApplicationApiService.scopes(List.of("has space")));
    }
}
