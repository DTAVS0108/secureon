package com.secureone.auth.account.otp;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.ClusterInitLock;
import com.secureone.auth.platform.PlatformSettingsService;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The server pepper for one-time codes and MFA recovery codes. {@code SECUREONE_OTP_PEPPER} when set. In production
 * without it, a random pepper is created once and stored in the platform settings, encrypted with the key kept
 * outside the database, so a database copy alone still cannot be searched offline. It never changes afterwards:
 * recovery codes are hashed with it. Development keeps the built-in default unless the variable is set.
 */
@Component
public class OtpPepper {

    public static final String DEFAULT = "change-me-in-production";
    static final String SETTING_KEY = "generated_secrets";
    static final String FIELD = "otpPepperEncrypted";

    private final String configured;
    private final boolean production;
    private final PlatformSettingsService settings;
    private final SecretProtector secrets;
    private final ClusterInitLock initLock;
    private volatile String resolved;

    public OtpPepper(
            @Value("${secureone.otp.pepper:" + DEFAULT + "}") String configured,
            SecureOneEnvironmentProperties environment,
            PlatformSettingsService settings,
            SecretProtector secrets,
            ClusterInitLock initLock) {
        this.configured = configured;
        this.production = environment.isProduction();
        this.settings = settings;
        this.secrets = secrets;
        this.initLock = initLock;
    }

    /** True when the pepper comes from SECUREONE_OTP_PEPPER (not the default). */
    public boolean configured() {
        return configured != null && !configured.isBlank() && !DEFAULT.equals(configured);
    }

    public String value() {
        String value = resolved;
        if (value != null) {
            return value;
        }
        synchronized (this) {
            if (resolved == null) {
                resolved = configured() || !production ? configured : initLock.readOrCreate(SETTING_KEY, this::readStored, this::create);
            }
            return resolved;
        }
    }

    private String readStored() {
        Object raw = settings.get(SETTING_KEY).get(FIELD);
        String pepper = raw == null ? "" : secrets.decrypt(raw.toString());
        return pepper.isBlank() ? null : pepper;
    }

    private String create() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String pepper = HexFormat.of().formatHex(bytes);
        Map<String, Object> stored = new HashMap<>(settings.get(SETTING_KEY));
        stored.put(FIELD, secrets.encrypt(pepper));
        settings.saveMap(SETTING_KEY, stored);
        return pepper;
    }
}
