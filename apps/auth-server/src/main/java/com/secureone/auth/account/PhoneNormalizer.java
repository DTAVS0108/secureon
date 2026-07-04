package com.secureone.auth.account;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public final class PhoneNormalizer {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNormalizer() {}

    /** Normalize to E.164 (e.g. +14155552671). Returns null if invalid. */
    public static String toE164(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Phonenumber.PhoneNumber parsed = UTIL.parse(raw.trim(), defaultRegion != null ? defaultRegion : "US");
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
