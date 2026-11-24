package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Which applications' two-step rule a browser session has met (single sign-on into the others checks theirs). */
class MfaDecidedApplicationsTest {

    @Test
    void anApplicationCountsOnlyForTheUserWhoSignedInThere() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        Authentication una = new TestingAuthenticationToken("acme:una@acme.test", "x");
        Authentication vic = new TestingAuthenticationToken("acme:vic@acme.test", "x");
        UUID portal = UUID.randomUUID();
        UUID hr = UUID.randomUUID();

        assertFalse(MfaLoginService.decidedFor(request, una, portal));
        MfaLoginService.markDecided(request, una, portal);
        assertTrue(MfaLoginService.decidedFor(request, una, portal));
        assertFalse(MfaLoginService.decidedFor(request, una, hr), "another application checks its own rule");
        MfaLoginService.markDecided(request, una, hr);
        assertTrue(MfaLoginService.decidedFor(request, una, portal) && MfaLoginService.decidedFor(request, una, hr));

        MfaLoginService.markDecided(request, vic, hr);
        assertTrue(MfaLoginService.decidedFor(request, vic, hr));
        assertFalse(MfaLoginService.decidedFor(request, vic, portal), "a new user in this browser starts from nothing");
        assertFalse(MfaLoginService.decidedFor(request, una, portal), "the earlier user's marks are gone");
    }

    @Test
    void noSessionNothingDecided() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Authentication una = new TestingAuthenticationToken("acme:una@acme.test", "x");
        MfaLoginService.markDecided(request, una, UUID.randomUUID());
        assertFalse(MfaLoginService.decidedFor(request, una, UUID.randomUUID()));
        assertFalse(MfaLoginService.decidedFor(request, una, null));
    }
}
