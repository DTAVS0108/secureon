package com.secureone.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SamlServiceProviderServiceTest {

    @Test
    void attributesAreCheckedAndNormalized() {
        var attributes = SamlServiceProviderService.attributes(List.of(
                Map.of("name", "mail", "source", "EMAIL"),
                Map.of("name", "dept", "source", "attribute", "value", "department"),
                Map.of("name", "urn:oid:2.5.4.42", "source", "given_name")));
        assertEquals("email", attributes.get(0).source());
        assertNull(attributes.get(0).value());
        assertEquals("department", attributes.get(1).value());
        assertThrows(IllegalArgumentException.class, () -> SamlServiceProviderService.attributes(List.of(
                Map.of("name", "a", "source", "email"), Map.of("name", "a", "source", "email"))), "duplicate");
        assertThrows(IllegalArgumentException.class, () -> SamlServiceProviderService.attributes(List.of(
                Map.of("name", "a", "source", "password"))), "unknown source");
        assertThrows(IllegalArgumentException.class, () -> SamlServiceProviderService.attributes(List.of(
                Map.of("name", "a", "source", "attribute", "value", "pendingEmail"))), "internal attribute");
        assertThrows(IllegalArgumentException.class, () -> SamlServiceProviderService.attributes(List.of(
                Map.of("name", "a b", "source", "email"))), "bad name");
    }

    @Test
    void urlsAreHttpsOrLoopback() {
        assertEquals("https://sp.example.com/acs", SamlServiceProviderService.url("acsUrl", "https://sp.example.com/acs", true));
        assertEquals("http://localhost:8080/acs", SamlServiceProviderService.url("acsUrl", "http://localhost:8080/acs", true));
        assertNull(SamlServiceProviderService.url("sloUrl", "", false));
        for (String bad : List.of("http://sp.example.com/acs", "javascript:alert(1)", "https://sp/acs#x", "")) {
            assertThrows(IllegalArgumentException.class, () -> SamlServiceProviderService.url("acsUrl", bad, true), bad);
        }
    }
}
