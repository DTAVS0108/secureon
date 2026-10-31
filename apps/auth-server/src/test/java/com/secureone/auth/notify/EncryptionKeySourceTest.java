package com.secureone.auth.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptionKeySourceTest {

    private static final String KEY = "base64:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static SecureOneEnvironmentProperties env(String name) {
        SecureOneEnvironmentProperties env = new SecureOneEnvironmentProperties();
        env.setEnvironment(name);
        return env;
    }

    @Test
    void theVariableWins(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("encryption.key"), "base64:other");
        EncryptionKeySource source = new EncryptionKeySource(KEY, dir.toString(), env("production"));
        assertEquals(EncryptionKeySource.Origin.ENVIRONMENT, source.origin());
        assertEquals(KEY, source.value());
    }

    @Test
    void anExistingKeyFileIsUsed(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("encryption.key"), KEY + "\n");
        EncryptionKeySource source = new EncryptionKeySource("", dir.toString(), env("production"));
        assertEquals(EncryptionKeySource.Origin.FILE, source.origin());
        assertEquals(KEY, source.value());
    }

    @Test
    void withoutAVolumeNothingIsGenerated(@TempDir Path dir) {
        // A temporary folder is not a mount point: a key written there would not survive the container.
        EncryptionKeySource dev = new EncryptionKeySource("", dir.toString(), env("development"));
        assertEquals(EncryptionKeySource.Origin.NONE, dev.origin());
        assertNull(dev.value());
        assertEquals(false, Files.exists(dir.resolve("encryption.key")));
        assertThrows(IllegalStateException.class, () -> new EncryptionKeySource("", dir.toString(), env("production")));
    }
}
