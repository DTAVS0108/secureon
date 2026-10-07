package com.secureone.auth.admin.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessReviewServiceTest {

    @Test
    void activeRecentlyUsedAccountWithMfaHasNoFlags() {
        assertEquals(List.of(), AccessReviewService.flags("ACTIVE", Instant.now().minus(Duration.ofDays(3)), true, null));
    }

    @Test
    void flagsEverythingAReviewerShouldLookAt() {
        assertEquals(
                List.of("ACCOUNT_NOT_ACTIVE", "NEVER_SIGNED_IN", "NO_MFA"),
                AccessReviewService.flags("DISABLED", null, false, null));
        assertEquals(
                List.of("UNUSED_90_DAYS"),
                AccessReviewService.flags("ACTIVE", Instant.now().minus(Duration.ofDays(120)), true, null));
    }
}
