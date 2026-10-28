package com.secureone.auth.account;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public final class PhoneNormalizer {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    /**
     * Country assumed for numbers typed without a +country code, unless the application's verification
     * policy sets its own ({@code defaultPhoneRegion}). SECUREONE_DEFAULT_PHONE_REGION, else US.
     */
    public static final String PLATFORM_DEFAULT_REGION = platformDefaultRegion();

    private PhoneNormalizer() {}

    private static String platformDefaultRegion() {
        String configured = System.getenv("SECUREONE_DEFAULT_PHONE_REGION");
        String region = normalizeRegion(configured);
        return region != null ? region : "US";
    }

    /** Upper-cased ISO 3166 code when libphonenumber knows the region, otherwise null. */
    public static String normalizeRegion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String region = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return UTIL.getSupportedRegions().contains(region) ? region : null;
    }

    /** Normalize to E.164 (+, country code, number). Returns null if invalid. */
    public static String toE164(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Phonenumber.PhoneNumber parsed = UTIL.parse(raw.trim(), defaultRegion != null ? defaultRegion : PLATFORM_DEFAULT_REGION);
            if (!UTIL.isValidNumber(parsed)) {
                return null;
            }
            return UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return null;
        }
    }

    public static String mask(String e164) {
        if (e164 == null || e164.length() < 4) {
            return "****";
        }
        return "****" + e164.substring(e164.length() - 4);
    }
}
