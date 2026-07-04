package com.secureone.auth.notify.sms;

import com.secureone.auth.notify.SecretProtector;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalizes SMS settings maps for storage, API responses, and provider resolution. */
public final class SmsSettingsMapper {

    public static final String SETTINGS_KEY = "sms";

    private SmsSettingsMapper() {}

    public static Map<String, Object> defaultSettings() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("enabled", false);
        defaults.put("provider", SmsProviderIds.LOG);
        defaults.put("twilio", defaultTwilioPublic());
        defaults.put("aws_sns", defaultAwsPublic());
        defaults.put("msg91", defaultMsg91Public());
        return defaults;
    }

    public static Map<String, Object> toPublicView(Map<String, Object> stored) {
        Map<String, Object> normalized = normalizeStored(stored);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", Boolean.TRUE.equals(normalized.get("enabled")));
        out.put("provider", string(normalized, "provider", SmsProviderIds.LOG));
        out.put("twilio", publicTwilio(map(normalized, "twilio")));
        out.put("aws_sns", publicAws(map(normalized, "aws_sns")));
        out.put("msg91", publicMsg91(map(normalized, "msg91")));
        out.put("configured", isConfigured(resolveCredentials(normalized, null)));
        out.put("smsConfigured", out.get("configured"));
        out.put("availableProviders", SmsProviderIds.ALL);
        return out;
    }

    public static Map<String, Object> mergeSave(Map<String, Object> current, Map<String, Object> body, SecretProtector secrets) {
        Map<String, Object> merged = normalizeStored(current);
        if (body.containsKey("enabled")) {
            merged.put("enabled", Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.containsKey("provider")) {
            merged.put("provider", normalizeProvider(string(body, "provider", SmsProviderIds.LOG)));
        }
        mergeTwilio(merged, map(body, "twilio"), secrets);
        mergeAws(merged, map(body, "aws_sns"), secrets);
        mergeMsg91(merged, map(body, "msg91"), secrets);
        mergeLegacyTopLevel(merged, body, secrets);
        return normalizeStored(merged);
    }

    public static SmsResolvedCredentials resolveCredentials(Map<String, Object> stored, SecretProtector secrets) {
        Map<String, Object> normalized = normalizeStored(stored);
        String provider = normalizeProvider(string(normalized, "provider", SmsProviderIds.LOG));
        boolean enabled = Boolean.TRUE.equals(normalized.get("enabled"));
        Map<String, Object> twilio = map(normalized, "twilio");
        Map<String, Object> aws = map(normalized, "aws_sns");
        Map<String, Object> msg91 = map(normalized, "msg91");
        return new SmsResolvedCredentials(
                provider,
                enabled,
                string(twilio, "accountSid", ""),
                decryptSecret(twilio, "authTokenEncrypted", "authToken", secrets),
                string(twilio, "fromNumber", ""),
                string(aws, "region", ""),
                string(aws, "accessKeyId", ""),
                decryptSecret(aws, "secretAccessKeyEncrypted", "secretAccessKey", secrets),
                string(aws, "senderId", ""),
                decryptSecret(msg91, "apiKeyEncrypted", "apiKey", secrets),
                string(msg91, "senderId", ""),
                string(msg91, "templateId", ""));
    }

    public static boolean isConfigured(SmsResolvedCredentials credentials) {
        if (credentials == null || !credentials.enabled()) {
            return true;
        }
        return switch (credentials.provider()) {
            case SmsProviderIds.LOG -> true;
            case SmsProviderIds.TWILIO -> !isBlank(credentials.twilioAccountSid())
                    && !isBlank(credentials.twilioAuthToken())
                    && !isBlank(credentials.twilioFromNumber());
            case SmsProviderIds.AWS_SNS -> !isBlank(credentials.awsRegion())
                    && !isBlank(credentials.awsAccessKeyId())
                    && !isBlank(credentials.awsSecretAccessKey());
            case SmsProviderIds.MSG91 -> !isBlank(credentials.msg91ApiKey());
            default -> false;
        };
    }

    private static Map<String, Object> normalizeStored(Map<String, Object> stored) {
        Map<String, Object> base = stored != null && !stored.isEmpty() ? new LinkedHashMap<>(stored) : defaultSettings();
        if (!base.containsKey("twilio")) {
            base.put("twilio", defaultTwilioStored());
        }
        if (!base.containsKey("aws_sns")) {
            base.put("aws_sns", defaultAwsStored());
        }
        if (!base.containsKey("msg91")) {
            base.put("msg91", defaultMsg91Stored());
        }
        migrateLegacyTopLevel(base);
        base.put("provider", normalizeProvider(string(base, "provider", SmsProviderIds.LOG)));
        return base;
    }

    private static void migrateLegacyTopLevel(Map<String, Object> base) {
        if (!isBlank(string(base, "twilioAccountSid", ""))) {
            Map<String, Object> twilio = map(base, "twilio");
            twilio.putIfAbsent("accountSid", string(base, "twilioAccountSid", ""));
            twilio.putIfAbsent("fromNumber", string(base, "twilioFromNumber", ""));
            if (!isBlank(string(base, "twilioAuthToken", ""))) {
                twilio.putIfAbsent("authToken", string(base, "twilioAuthToken", ""));
            }
            base.put("twilio", twilio);
        }
        base.remove("twilioAccountSid");
        base.remove("twilioAuthToken");
        base.remove("twilioFromNumber");
    }

    private static void mergeLegacyTopLevel(Map<String, Object> merged, Map<String, Object> body, SecretProtector secrets) {
        Map<String, Object> twilio = new HashMap<>(map(merged, "twilio"));
        boolean changed = false;
        if (body.containsKey("twilioAccountSid")) {
            twilio.put("accountSid", string(body, "twilioAccountSid", ""));
            changed = true;
        }
        if (body.containsKey("twilioFromNumber")) {
            twilio.put("fromNumber", string(body, "twilioFromNumber", ""));
            changed = true;
        }
        if (body.containsKey("twilioAuthToken")) {
            encryptField(twilio, "authToken", body.get("twilioAuthToken"), secrets);
            changed = true;
        }
        if (changed) {
            merged.put("twilio", twilio);
        }
    }

    private static void mergeTwilio(Map<String, Object> merged, Map<String, Object> body, SecretProtector secrets) {
        if (body.isEmpty()) {
            return;
        }
        Map<String, Object> twilio = new HashMap<>(map(merged, "twilio"));
        if (body.containsKey("accountSid")) {
            twilio.put("accountSid", string(body, "accountSid", ""));
        }
        if (body.containsKey("fromNumber")) {
            twilio.put("fromNumber", string(body, "fromNumber", ""));
        }
        if (body.containsKey("authToken")) {
            encryptField(twilio, "authToken", body.get("authToken"), secrets);
        }
        merged.put("twilio", twilio);
    }

    private static void mergeAws(Map<String, Object> merged, Map<String, Object> body, SecretProtector secrets) {
        if (body.isEmpty()) {
            return;
        }
        Map<String, Object> aws = new HashMap<>(map(merged, "aws_sns"));
        if (body.containsKey("region")) {
            aws.put("region", string(body, "region", ""));
        }
        if (body.containsKey("accessKeyId")) {
            aws.put("accessKeyId", string(body, "accessKeyId", ""));
        }
        if (body.containsKey("senderId")) {
            aws.put("senderId", string(body, "senderId", ""));
        }
        if (body.containsKey("secretAccessKey")) {
            encryptField(aws, "secretAccessKey", body.get("secretAccessKey"), secrets);
        }
        merged.put("aws_sns", aws);
    }

    private static void mergeMsg91(Map<String, Object> merged, Map<String, Object> body, SecretProtector secrets) {
        if (body.isEmpty()) {
            return;
        }
        Map<String, Object> msg91 = new HashMap<>(map(merged, "msg91"));
        if (body.containsKey("senderId")) {
            msg91.put("senderId", string(body, "senderId", ""));
        }
        if (body.containsKey("templateId")) {
            msg91.put("templateId", string(body, "templateId", ""));
        }
        if (body.containsKey("apiKey")) {
            encryptField(msg91, "apiKey", body.get("apiKey"), secrets);
        }
        merged.put("msg91", msg91);
    }

    private static Map<String, Object> publicTwilio(Map<String, Object> twilio) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountSid", string(twilio, "accountSid", ""));
        out.put("fromNumber", string(twilio, "fromNumber", ""));
        out.put("authTokenConfigured", hasSecret(twilio, "authTokenEncrypted", "authToken"));
        return out;
    }

    private static Map<String, Object> publicAws(Map<String, Object> aws) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("region", string(aws, "region", ""));
        out.put("accessKeyId", string(aws, "accessKeyId", ""));
        out.put("senderId", string(aws, "senderId", ""));
        out.put("secretAccessKeyConfigured", hasSecret(aws, "secretAccessKeyEncrypted", "secretAccessKey"));
        return out;
    }

    private static Map<String, Object> publicMsg91(Map<String, Object> msg91) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("senderId", string(msg91, "senderId", ""));
        out.put("templateId", string(msg91, "templateId", ""));
        out.put("apiKeyConfigured", hasSecret(msg91, "apiKeyEncrypted", "apiKey"));
        return out;
    }

    private static Map<String, Object> defaultTwilioPublic() {
        return publicTwilio(defaultTwilioStored());
    }

    private static Map<String, Object> defaultAwsPublic() {
        return publicAws(defaultAwsStored());
    }

    private static Map<String, Object> defaultMsg91Public() {
        return publicMsg91(defaultMsg91Stored());
    }

    private static Map<String, Object> defaultTwilioStored() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountSid", "");
        m.put("fromNumber", "");
        return m;
    }

    private static Map<String, Object> defaultAwsStored() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region", "ap-south-1");
        m.put("accessKeyId", "");
        m.put("senderId", "");
        return m;
    }

    private static Map<String, Object> defaultMsg91Stored() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("senderId", "");
        m.put("templateId", "");
        return m;
    }

    private static void encryptField(Map<String, Object> target, String plainKey, Object plainValue, SecretProtector secrets) {
        if (plainValue == null) {
            return;
        }
        String plain = plainValue.toString();
        if (plain.isBlank()) {
            return;
        }
        target.put(plainKey + "Encrypted", secrets.encrypt(plain));
        target.remove(plainKey);
    }

    private static String decryptSecret(
            Map<String, Object> map, String encryptedKey, String plainKey, SecretProtector secrets) {
        String encrypted = string(map, encryptedKey, "");
        if (!encrypted.isBlank() && secrets != null) {
            return secrets.decrypt(encrypted);
        }
        return string(map, plainKey, "");
    }

    private static boolean hasSecret(Map<String, Object> map, String encryptedKey, String plainKey) {
        return !isBlank(string(map, encryptedKey, "")) || !isBlank(string(map, plainKey, ""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        Object raw = parent.get(key);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null) {
            return SmsProviderIds.LOG;
        }
        String normalized = provider.trim().toLowerCase();
        return SmsProviderIds.ALL.contains(normalized) ? normalized : SmsProviderIds.LOG;
    }

    private static String string(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : defaultValue;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
