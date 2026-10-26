package com.secureone.auth.oauth;

import java.util.Optional;
import java.util.UUID;

/** Test access to package-private helpers. */
public final class BackchannelLogoutAccess {
    private BackchannelLogoutAccess() {}

    public static String subjectFor(OAuthClient client, String principalName, Optional<UUID> userId) {
        return BackchannelLogoutService.subjectFor(client, principalName, userId);
    }

    public static boolean isLocalDevelopment(String uri) {
        return BackchannelLogoutService.isLocalDevelopment(uri);
    }
}
