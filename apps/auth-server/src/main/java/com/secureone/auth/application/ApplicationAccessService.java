package com.secureone.auth.application;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Decides whether a signed-in tenant user may use an application. Existing members are admitted;
 * non-members are enrolled with the default role only when the app allows self registration.
 */
@Service
public class ApplicationAccessService {

    static final String SELF_REGISTRATION_FEATURE = "self_registration";

    private final ApplicationMembershipService memberships;
    private final ApplicationEffectiveSettingsService effectiveSettings;

    public ApplicationAccessService(
            ApplicationMembershipService memberships, ApplicationEffectiveSettingsService effectiveSettings) {
        this.memberships = memberships;
        this.effectiveSettings = effectiveSettings;
    }

    /** Returns {@code true} when the user is (or has just become) a member of the application. */
    public boolean admit(UUID applicationId, UUID userId) {
        UUID previous = ApplicationSchemaContext.getApplicationId();
        // Membership and roles may live in the app's own schema; route this lookup there explicitly.
        ApplicationSchemaContext.setApplicationId(applicationId);
        try {
            if (memberships.isMember(applicationId, userId)) {
                return true;
            }
            if (!effectiveSettings.isFeatureEnabled(applicationId, SELF_REGISTRATION_FEATURE)) {
                return false;
            }
            memberships.ensureMemberWithDefaultRole(applicationId, userId);
            return true;
        } finally {
            ApplicationSchemaContext.setApplicationId(previous);
        }
    }
}
