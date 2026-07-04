package com.secureone.auth.account.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OtpGeneratorTest {

    @Test
    void generatesSixDigitCode() {
        String code = OtpGenerator.generate(6);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void hashesAreDeterministic() {
        var id = java.util.UUID.randomUUID();
        String hash1 = OtpHasher.hash(id, "123456", "pepper");
        String hash2 = OtpHasher.hash(id, "123456", "pepper");
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, OtpHasher.hash(id, "654321", "pepper"));
    }
}
