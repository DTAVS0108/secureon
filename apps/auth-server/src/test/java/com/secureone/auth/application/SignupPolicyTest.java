package com.secureone.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignupPolicyTest {

    private static SignupPolicy policy(Map<String, Object> body) {
        return SignupPolicy.sanitize(body);
    }

    @Test
    void noRulesAllowsEveryDomain() {
        assertNull(SignupPolicy.NONE.domainRefusal("anyone@anywhere.org"));
    }

    @Test
    void allowedDomainsAreExactAndWildcardMeansSubdomainsOnly() {
        SignupPolicy p = policy(Map.of("allowedDomains", List.of("Example.com", "@*.corp.example")));
        assertNull(p.domainRefusal("a@example.com"));
        assertNotNull(p.domainRefusal("a@sub.example.com"));
        assertNull(p.domainRefusal("a@eu.corp.example"));
        assertNotNull(p.domainRefusal("a@corp.example"));
        assertNotNull(p.domainRefusal("a@other.org"));
    }

    @Test
    void blockedWinsOverAllowed() {
        SignupPolicy p = policy(Map.of("allowedDomains", List.of("*.example.com"), "blockedDomains", List.of("temp.example.com")));
        assertNull(p.domainRefusal("a@eu.example.com"));
        assertNotNull(p.domainRefusal("a@temp.example.com"));
    }

    @Test
    void notADomainIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> policy(Map.of("blockedDomains", List.of("not a domain"))));
        assertThrows(IllegalArgumentException.class, () -> policy(Map.of("allowedDomains", List.of("localhost"))));
    }

    @Test
    void fieldKeysMustBeValidUniqueAndNotReserved() {
        assertThrows(IllegalArgumentException.class, () -> policy(Map.of("fields", List.of(Map.of("key", "1st", "label", "x")))));
        assertThrows(IllegalArgumentException.class, () -> policy(Map.of("fields", List.of(Map.of("key", "authMethods", "label", "x")))));
        assertThrows(IllegalArgumentException.class, () -> policy(Map.of("fields",
                List.of(Map.of("key", "company", "label", "a"), Map.of("key", "company", "label", "b")))));
        assertThrows(IllegalArgumentException.class, () -> policy(Map.of("fields", List.of(Map.of("key", "plan", "label", "Plan", "type", "select")))));
    }

    @Test
    void submittedValuesAreCheckedAndUnknownOnesDropped() {
        SignupPolicy p = policy(Map.of("fields", List.of(
                Map.of("key", "company", "label", "Company", "required", true, "maxLength", 5),
                Map.of("key", "plan", "label", "Plan", "type", "select", "options", List.of("free", "pro")),
                Map.of("key", "terms", "label", "Terms", "type", "checkbox", "required", true))));

        Map<String, Object> saved = p.attributesFrom(Map.of("company", " Acme ", "plan", "pro", "terms", true, "injected", "x"));
        assertEquals(Map.of("company", "Acme", "plan", "pro", "terms", true), saved);
        assertFalse(saved.containsKey("injected"));

        assertThrows(IllegalArgumentException.class, () -> p.attributesFrom(Map.of("plan", "pro", "terms", true)));
        assertThrows(IllegalArgumentException.class, () -> p.attributesFrom(Map.of("company", "Acme", "terms", false)));
        assertThrows(IllegalArgumentException.class, () -> p.attributesFrom(Map.of("company", "Acme", "terms", true, "plan", "gold")));
        assertThrows(IllegalArgumentException.class, () -> p.attributesFrom(Map.of("company", "Acme Ltd", "terms", true)));
    }

    @Test
    void savedPolicyRoundTrips() {
        SignupPolicy p = policy(Map.of("allowedDomains", List.of("example.com"),
                "fields", List.of(Map.of("key", "company", "label", "Company"))));
        SignupPolicy again = SignupPolicy.parse(p.toMap());
        assertEquals(p, again);
        assertTrue(SignupPolicy.parse("not a map").isEmpty());
    }
}
