package com.secureone.auth.mfa.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.yubico.webauthn.data.ByteArray;
import org.junit.jupiter.api.Test;

class WebAuthnCredentialRepositoryTest {

    @Test
    void encodesAndDecodesByteArrays() {
        ByteArray original = new ByteArray(new byte[] {1, 2, 3, 4, 5});
        String encoded = WebAuthnCredentialRepository.encode(original);
        ByteArray decoded = WebAuthnCredentialRepository.decode(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void usernameUsesTenantSlugAndEmail() {
        assertEquals("acme:alice@example.com", PasskeyRegistrationService.username("Acme", "Alice@Example.com"));
        assertNotEquals(
                PasskeyRegistrationService.username("acme", "alice@example.com"),
                PasskeyRegistrationService.username("other", "alice@example.com"));
    }
}
