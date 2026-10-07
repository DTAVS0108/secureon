package com.secureone.auth.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WebhookTargetPolicyTest {

    private final WebhookTargetPolicy strict = new WebhookTargetPolicy(false);

    @Test
    void rejectsNonHttpAndMalformedUrls() {
        for (String bad : List.of("javascript:alert(1)", "ftp://example.com/x", "not a url", "https://user:pw@example.com/", "https://example.com/#x")) {
            assertThrows(ResponseStatusException.class, () -> strict.validateForRegistration(bad), bad);
        }
    }

    @Test
    void rejectsInternalTargets() {
        for (String internal : List.of(
                "http://localhost:9000/api",
                "http://127.0.0.1:6379/",
                "http://169.254.169.254/latest/meta-data/",
                "http://10.0.0.5/hook",
                "http://192.168.1.10/hook",
                "http://172.16.0.1/hook",
                "http://[::1]/hook",
                "http://0.0.0.0/hook",
                "http://svc.internal/hook")) {
            assertThrows(ResponseStatusException.class, () -> strict.validateForRegistration(internal), internal);
            assertNotNull(strict.deliveryProblem(internal), internal);
        }
    }

    @Test
    void acceptsPublicHttpsTarget() {
        assertDoesNotThrow(() -> strict.validateForRegistration("https://93.184.215.14/hook"));
        assertNull(strict.deliveryProblem("https://93.184.215.14/hook"));
    }

    @Test
    void localTestingFlagAllowsPrivateTargets() {
        WebhookTargetPolicy permissive = new WebhookTargetPolicy(true);
        assertDoesNotThrow(() -> permissive.validateForRegistration("http://localhost:4000/hook"));
    }

    @Test
    void classifiesSpecialRanges() throws Exception {
        assertTrue(WebhookTargetPolicy.isInternal(InetAddress.getByName("100.64.1.1")));
        assertTrue(WebhookTargetPolicy.isInternal(InetAddress.getByName("fd00::1")));
        assertFalse(WebhookTargetPolicy.isInternal(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void validatesEventNames() {
        assertDoesNotThrow(() -> WebhookTargetPolicy.validateEvents(List.of("user.created", "*")));
        assertThrows(ResponseStatusException.class, () -> WebhookTargetPolicy.validateEvents(List.of("not.an.event")));
    }
}
