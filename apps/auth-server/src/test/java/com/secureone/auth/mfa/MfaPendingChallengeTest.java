package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MfaPendingChallengeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void setupChallengeSurvivesTheRedisRoundTrip() throws Exception {
        MfaPendingChallenge setup = new MfaPendingChallenge(UUID.randomUUID(), UUID.randomUUID(), "tenant-a", "a@tenant-a.test",
                UUID.randomUUID(), null, "setup", null, null, "factor-1");
        MfaPendingChallenge read = mapper.readValue(mapper.writeValueAsString(setup), MfaPendingChallenge.class);
        assertEquals(setup, read);
        assertTrue(read.isSetup());
    }

    @Test
    void entriesWithExtraFieldsStillRead() throws Exception {
        String raw = "{\"challengeId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID()
                + "\",\"tenantSlug\":\"tenant-a\",\"email\":\"a@tenant-a.test\",\"stage\":\"setup\",\"setup\":true}";
        assertTrue(mapper.readValue(raw, MfaPendingChallenge.class).isSetup());
    }
}
