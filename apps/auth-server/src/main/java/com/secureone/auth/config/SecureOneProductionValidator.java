package com.secureone.auth.config;

import com.secureone.auth.notify.EncryptionKeySource;
import com.secureone.auth.platform.PlatformSettingsService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Rejects unsafe defaults when running with the production profile or environment. */
@Component
public class SecureOneProductionValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecureOneProductionValidator.class);

    private final SecureOneEnvironmentProperties environment;
    private final PlatformSettingsService platformSettings;
    private final EncryptionKeySource encryptionKey;
    private final String devUsername;
    private final String devPassword;
    private final String rlsEnforce;
    private final boolean breakGlassEnabled;
    private final String issuerUrl;
    private final String publicBaseUrl;

    public SecureOneProductionValidator(
            SecureOneEnvironmentProperties environment,
            PlatformSettingsService platformSettings,
            EncryptionKeySource encryptionKey,
            @Value("${spring.security.user.name:admin}") String devUsername,
            @Value("${spring.security.user.password:admin}") String devPassword,
            @Value("${secureone.rls.enforce:off}") String rlsEnforce,
            @Value("${secureone.break-glass.enabled:false}") boolean breakGlassEnabled,
            @Value("${spring.security.oauth2.authorizationserver.issuer:}") String issuerUrl,
            @Value("${secureone.public-base-url:}") String publicBaseUrl) {
        this.environment = environment;
        this.platformSettings = platformSettings;
        this.encryptionKey = encryptionKey;
        this.devUsername = stripNoopPrefix(devUsername);
        this.devPassword = stripNoopPrefix(devPassword);
        this.rlsEnforce = rlsEnforce;
        this.breakGlassEnabled = breakGlassEnabled;
        this.issuerUrl = issuerUrl;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.isProduction()) {
            return;
        }
        // The code pepper and the token signing key are created on the first start when not set (OtpPepper,
        // OAuth2SigningKeyService), stored encrypted with the key below.
        if (environment.isDevAuthEnabled()) {
            throw new IllegalStateException(
                    "SECUREONE_DEV_AUTH_ENABLED must be false in production.");
        }
        // The emergency account only signs in while break-glass is on; first-run setup (/admin/setup)
        // creates the first named operator, so production no longer needs this account at all.
        if (breakGlassEnabled && "admin".equals(devUsername) && "admin".equals(devPassword)) {
            throw new IllegalStateException(
                    "Default admin credentials (admin/admin) are not allowed in production.");
        }
        if (breakGlassEnabled) {
            if (!devPassword.startsWith("{bcrypt}") && !devPassword.startsWith("$2")) {
                throw new IllegalStateException(
                        "The break-glass password (SECUREONE_DEV_PASSWORD) must be a bcrypt hash in production.");
            }
            log.warn("Break-glass platform account '{}' is enabled. Disable SECUREONE_BREAK_GLASS_ENABLED once "
                    + "named platform operators can sign in.", devUsername);
        }
        requireHttps("SECUREONE_ISSUER_URL", issuerUrl);
        requireHttps("SECUREONE_PUBLIC_BASE_URL", publicBaseUrl);
        if (encryptionKey.origin() == EncryptionKeySource.Origin.NONE) {
            throw new IllegalStateException("Production needs an encryption key outside the database: mount a volume at "
                    + encryptionKey.file().getParent() + " or set SECUREONE_ENCRYPTION_KEY.");
        }
        warnIfSmsProviderIsLog();
        if (!"on".equalsIgnoreCase(rlsEnforce) && !"true".equalsIgnoreCase(rlsEnforce)) {
            log.warn("Row-level security is not enforced (SECUREONE_RLS_ENFORCE=off). Fix the 'RLS unscoped' "
                    + "warnings from staging, then enable it.");
        }
    }

    /** Tokens, cookies and redirects carry this URL; production must be reached over TLS. */
    private static void requireHttps(String variable, String url) {
        if (url == null || !url.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException(variable + " must be an https:// URL in production (was '" + url
                    + "'). Terminate TLS at your load balancer and set the public URL users see.");
        }
    }

    private void warnIfSmsProviderIsLog() {
        Map<String, Object> sms = platformSettings.get("sms");
        Object provider = sms.get("provider");
        if ("log".equalsIgnoreCase(String.valueOf(provider))) {
            log.warn(
                    "SMS provider is set to 'log' in production — configure a real provider in Platform settings.");
        }
    }

    private static String stripNoopPrefix(String value) {
        if (value != null && value.startsWith("{noop}")) {
            return value.substring("{noop}".length());
        }
        return value;
    }
}
