package com.secureone.auth.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secureone.auth.platform.VerificationPolicyDefaults;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhoneNormalizerRegionTest {

    @Test
    void readsNationalNumbersInTheGivenCountry() {
        assertEquals("+919811077701", PhoneNormalizer.toE164("98110 77701", "IN"));
        assertEquals("+919811077701", PhoneNormalizer.toE164("098110-77701", "IN"));
        // An explicit country code wins over the default region.
        assertEquals("+14155552671", PhoneNormalizer.toE164("+1 415 555 2671", "IN"));
    }

    @Test
    void normalizesOnlyKnownRegions() {
        assertEquals("IN", PhoneNormalizer.normalizeRegion(" in "));
        assertNull(PhoneNormalizer.normalizeRegion("ZZ"));
        assertNull(PhoneNormalizer.normalizeRegion(""));
    }

    @Test
    void verificationPolicyValidatesTheDefaultCountry() {
        assertEquals("IN", VerificationPolicyDefaults.sanitizeForSave(Map.of("defaultPhoneRegion", "in")).get("defaultPhoneRegion"));
        assertThrows(IllegalArgumentException.class,
                () -> VerificationPolicyDefaults.sanitizeForSave(Map.of("defaultPhoneRegion", "zz")));
        assertEquals(PhoneNormalizer.PLATFORM_DEFAULT_REGION,
                VerificationPolicyDefaults.sanitizeForSave(Map.of()).get("defaultPhoneRegion"));
    }
}
