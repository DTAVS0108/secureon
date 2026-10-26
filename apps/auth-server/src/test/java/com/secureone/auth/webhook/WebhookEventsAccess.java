package com.secureone.auth.webhook;

import java.util.Collection;

/** Test access to package-private helpers. */
public final class WebhookEventsAccess {
    private WebhookEventsAccess() {}

    public static Collection<String> fromAuditValues() {
        return WebhookEvents.FROM_AUDIT.values();
    }
}
