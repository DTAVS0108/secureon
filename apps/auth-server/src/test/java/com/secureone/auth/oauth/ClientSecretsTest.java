package com.secureone.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientSecretsTest {

    @Test
    void secretsAreStoredAsBcryptAndStillMatch() {
        String hashed = ClientSecrets.hash("s3cret-value");
        assertTrue(hashed.startsWith("{bcrypt}$2"), hashed);
        assertFalse(hashed.contains("s3cret-value"));
        assertNotEquals(hashed, ClientSecrets.hash("s3cret-value"), "salted");
        assertTrue(ClientSecrets.isHashed(hashed));
        assertTrue(ClientSecrets.matches("s3cret-value", hashed));
        assertFalse(ClientSecrets.matches("wrong", hashed));
        // Hash without the prefix (as some tools write it)
        assertTrue(ClientSecrets.matches("s3cret-value", hashed.substring("{bcrypt}".length())));
    }

    @Test
    void legacyPlainSecretsStillWorkUntilHashed() {
        assertFalse(ClientSecrets.isHashed("plain-secret"));
        assertTrue(ClientSecrets.matches("plain-secret", "plain-secret"));
        assertFalse(ClientSecrets.matches("other", "plain-secret"));
        assertFalse(ClientSecrets.matches("x", null));
        assertFalse(ClientSecrets.matches(null, "x"));
        assertFalse(ClientSecrets.matches("", ""));
    }
}
