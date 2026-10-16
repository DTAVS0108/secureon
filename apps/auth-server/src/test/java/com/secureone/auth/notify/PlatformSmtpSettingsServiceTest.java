package com.secureone.auth.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.platform.PlatformSettingsService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformSmtpSettingsServiceTest {

    private final PlatformSettingsService settings = mock(PlatformSettingsService.class);
    private final SecretProtector secrets = mock(SecretProtector.class);

    private PlatformSmtpSettingsService service(String envHost) {
        return new PlatformSmtpSettingsService(settings, secrets, envHost, 1025, "", "", false, false);
    }

    @Test
    void usesTheEnvironmentServerUntilOneIsSaved() {
        // A fresh installation: the catalog seeds an empty host.
        when(settings.get("smtp")).thenReturn(Map.of("host", "", "port", 465, "security", "ssl"));
        PlatformSmtpSettingsService smtp = service("mailhog");

        SmtpSettingsService.SmtpConnectionConfig config = smtp.loadConnectionConfig();
        assertEquals("mailhog", config.host());
        assertEquals(1025, config.port());
        assertEquals("none", config.security());
        assertTrue(smtp.isConfigured());
        assertEquals("environment", smtp.getPublicSettings().get("source"));
    }

    @Test
    void savedSettingsWinOverTheEnvironment() {
        when(settings.get("smtp")).thenReturn(Map.of("host", "smtp.example.com", "port", 587, "security", "starttls"));
        PlatformSmtpSettingsService smtp = service("mailhog");

        assertEquals("smtp.example.com", smtp.loadConnectionConfig().host());
        assertEquals("settings", smtp.getPublicSettings().get("source"));
    }

    @Test
    void noServerAnywhereMeansNotConfigured() {
        when(settings.get("smtp")).thenReturn(Map.of("host", ""));
        PlatformSmtpSettingsService smtp = service("");

        assertNull(smtp.loadConnectionConfig());
        assertFalse(smtp.isConfigured());
    }
}
