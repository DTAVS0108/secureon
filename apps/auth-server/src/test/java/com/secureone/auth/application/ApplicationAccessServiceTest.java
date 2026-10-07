package com.secureone.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationAccessServiceTest {

    private final UUID appId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Mock
    private ApplicationMembershipService memberships;
    @Mock
    private ApplicationEffectiveSettingsService effectiveSettings;
    @InjectMocks
    private ApplicationAccessService access;

    @AfterEach
    void clearContext() {
        ApplicationSchemaContext.clear();
    }

    @Test
    void existingMemberIsAdmittedWithoutEnrollment() {
        when(memberships.isMember(appId, userId)).thenReturn(true);

        assertTrue(access.admit(appId, userId));
        verify(memberships, never()).ensureMemberWithDefaultRole(any(), any());
    }

    @Test
    void nonMemberIsEnrolledWhenSelfRegistrationIsOn() {
        when(memberships.isMember(appId, userId)).thenReturn(false);
        when(effectiveSettings.isFeatureEnabled(appId, "self_registration")).thenReturn(true);

        assertTrue(access.admit(appId, userId));
        verify(memberships).ensureMemberWithDefaultRole(appId, userId);
    }

    @Test
    void nonMemberIsRefusedWhenSelfRegistrationIsOff() {
        when(memberships.isMember(appId, userId)).thenReturn(false);
        when(effectiveSettings.isFeatureEnabled(appId, "self_registration")).thenReturn(false);

        assertFalse(access.admit(appId, userId));
        verify(memberships, never()).ensureMemberWithDefaultRole(any(), any());
    }

    @Test
    void lookupRunsInTheAppSchemaAndRestoresTheCallerContext() {
        UUID callerApp = UUID.randomUUID();
        ApplicationSchemaContext.setApplicationId(callerApp);
        when(memberships.isMember(appId, userId)).thenAnswer(invocation -> {
            assertEquals(appId, ApplicationSchemaContext.getApplicationId());
            return true;
        });

        access.admit(appId, userId);

        assertEquals(callerApp, ApplicationSchemaContext.getApplicationId());
        ApplicationSchemaContext.clear();
        access.admit(appId, userId);
        assertNull(ApplicationSchemaContext.getApplicationId());
    }
}
