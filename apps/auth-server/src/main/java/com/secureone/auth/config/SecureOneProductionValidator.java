package com.secureone.auth.config;

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
    private static final String DEFAULT_OTP_PEPPER = "change-me-in-production";

    private final SecureOneEnvironmentProperties environment;
    private final PlatformSettingsService platformSettings;
    private final String otpPepper;
    private final String devUsername;
    private final String devPassword;

    public SecureOneProductionValidator(
            SecureOneEnvironmentProperties environment,
            PlatformSettingsService platformSettings,
            @Value("${secureone.otp.pepper:change-me-in-production}") String otpPepper,
            @Value("${spring.security.user.name:admin}") String devUsername,
            @Value("${spring.security.user.password:admin}") String devPassword) {
        this.environment = environment;
        this.platformSettings = platformSettings;
        this.otpPepper = otpPepper;
        this.devUsername = stripNoopPrefix(devUsername);
        this.devPassword = stripNoopPrefix(devPassword);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.isProduction()) {
            return;
        }
        if (DEFAULT_OTP_PEPPER.equals(otpPepper)) {
            throw new IllegalStateException(
                    "SECUREONE_OTP_PEPPER must be set to a strong secret in production (not the default).");
        }
        if (environment.isDevAuthEnabled()) {
            throw new IllegalStateException(
                    "SECUREONE_DEV_AUTH_ENABLED must be false in production.");
        }
        if ("admin".equals(devUsername) && "admin".equals(devPassword)) {
            throw new IllegalStateException(
                    "Default admin credentials (admin/admin) are not allowed in production.");
        }
        warnIfSmsProviderIsLog();
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
