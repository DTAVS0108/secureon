package com.secureone.auth.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LeakedPasswordServiceTest {

    @Test
    void sha1HexMatchesKnownValue() {
        String hash = LeakedPasswordService.sha1Hex("password");
        assertEquals(40, hash.length());
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash);
    }

    @Test
    void detectsSuffixInRangeResponse() {
        String hash = LeakedPasswordService.sha1Hex("password");
        String body = hash.substring(5) + ":3730471\r\nDD2EBC505D6018F7438376538C0D13251E966690:3931";
        assertTrue(LeakedPasswordService.containsSuffix(body, hash.substring(5)));
        assertFalse(LeakedPasswordService.containsSuffix(body, "00000"));
    }

    @Test
    void assertNotLeakedSkipsWhenPolicyDisabled() {
        SecureOneEnvironmentProperties env = new SecureOneEnvironmentProperties();
        LeakedPasswordService service = new LeakedPasswordService(env, true, RestClient.create());
        service.assertNotLeaked("password", false);
    }

    @Test
    void breachCheckSanitizedInPolicy() {
        // NIST SP 800-63B: passwords are checked against leaked lists by default.
        org.junit.jupiter.api.Assertions.assertTrue(com.secureone.auth.application.PasswordPolicySanitizer.breachCheckEnabled(
                com.secureone.auth.application.PasswordPolicySanitizer.platformDefaults()));
    }
}
