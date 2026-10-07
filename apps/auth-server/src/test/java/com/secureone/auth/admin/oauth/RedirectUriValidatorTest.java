package com.secureone.auth.admin.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedirectUriValidatorTest {

    @Test
    void acceptsWebLoopbackAndNativeAppUris() {
        for (String ok : List.of(
                "https://app.example.com/callback",
                "http://localhost:3000/cb",
                "http://127.0.0.1:8080/cb?x=1",
                "com.example.app:/oauth2redirect")) {
            assertNull(RedirectUriValidator.problem(ok), ok);
        }
    }

    @Test
    void rejectsDangerousOrMalformedUris() {
        for (String bad : List.of(
                "javascript:alert(1)",
                "data:text/html,hi",
                "file:///etc/passwd",
                "not a url",
                "/relative/cb",
                "https://app.example.com/cb#frag",
                "https://*.example.com/cb",
                "myapp:/cb",
                "https:///nohost")) {
            assertNotNull(RedirectUriValidator.problem(bad), bad);
        }
    }

    @Test
    void validateTrimsDeduplicatesAndNamesTheBadUri() {
        assertEquals(
                List.of("https://a.example/cb"),
                RedirectUriValidator.validate(
                        Arrays.asList(" https://a.example/cb ", "https://a.example/cb", "", null), "redirectUri"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RedirectUriValidator.validate(List.of("javascript:alert(1)"), "redirectUri"));
        assertTrue(ex.getMessage().contains("javascript:alert(1)"));
    }
}
