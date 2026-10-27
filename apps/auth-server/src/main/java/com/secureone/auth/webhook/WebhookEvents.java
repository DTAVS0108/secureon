package com.secureone.auth.webhook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The events tenants can subscribe webhooks to, with a description for the console. Most come from the audit log
 * ({@link AuditWebhookBridge} maps the audit action to the event); sign-ins come from the sign-in handlers.
 */
public final class WebhookEvents {

    private WebhookEvents() {}

    /** Event → description, in the order the console lists them. */
    public static final Map<String, String> CATALOG;

    /** Audit action → webhook event (several audit actions can be one event). */
    static final Map<String, String> FROM_AUDIT;

    static {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("user.created", "A user was created (by an admin, an import or self-registration)");
        c.put("user.updated", "A user's profile or status was changed");
        c.put("user.deleted", "A user was deleted");
        c.put("user.suspended", "A user was suspended");
        c.put("user.locked", "A user was locked after failed sign-ins");
        c.put("user.unlocked", "A user was unlocked");
        c.put("user.email_verified", "A user verified their email address");
        c.put("user.email_changed", "A user changed their email address");
        c.put("user.password_changed", "A user's password was set, reset or removed");
        c.put("user.two_factor_enrolled", "A user added a two-step option");
        c.put("user.two_factor_removed", "A user's two-step option was removed or reset");
        c.put("user.roles_changed", "A user's roles in an application were changed");
        c.put("user.application_access_granted", "A user was given access to an application");
        c.put("user.application_access_revoked", "A user's access to an application was removed");
        c.put("user.login.succeeded", "A user signed in");
        c.put("user.login.failed", "A sign-in failed");
        c.put("session.revoked", "A sign-in session was ended");
        c.put("role.created", "A role was created");
        c.put("role.updated", "A role or its permissions changed");
        c.put("role.deleted", "A role was deleted");
        c.put("group.changed", "A group was created, changed or deleted");
        c.put("account.deletion_requested", "A user asked for their account to be deleted");
        c.put("access_request.created", "Someone asked for access");
        c.put("access_request.approved", "An access request was approved");
        c.put("access_request.denied", "An access request was denied");
        CATALOG = java.util.Collections.unmodifiableMap(c);

        Map<String, String> a = new LinkedHashMap<>();
        a.put("user.self_registered", "user.created");
        a.put("user.import", "user.created");
        a.put("user.updated", "user.updated");
        a.put("user.status_changed", "user.updated");
        a.put("user.deleted", "user.deleted");
        a.put("user.suspended", "user.suspended");
        a.put("user.locked", "user.locked");
        a.put("user.login.locked", "user.locked");
        a.put("user.unlocked", "user.unlocked");
        a.put("user.email_verified", "user.email_verified");
        a.put("user.email_changed", "user.email_changed");
        a.put("user.password_set", "user.password_changed");
        a.put("user.password_reset", "user.password_changed");
        a.put("user.password_removed", "user.password_changed");
        a.put("user.password_changed", "user.password_changed");
        a.put("user.two_factor_enrolled", "user.two_factor_enrolled");
        a.put("user.mfa_removed", "user.two_factor_removed");
        a.put("user.mfa_factor_removed", "user.two_factor_removed");
        a.put("user.mfa_reset", "user.two_factor_removed");
        a.put("user.mfa_reset_method", "user.two_factor_removed");
        a.put("user.two_step_reset", "user.two_factor_removed");
        a.put("two_step.factor_removed", "user.two_factor_removed");
        a.put("user.roles_changed", "user.roles_changed");
        a.put("user.role_granted", "user.roles_changed");
        a.put("user.role_revoked", "user.roles_changed");
        a.put("user.role_expired", "user.roles_changed");
        a.put("user.attributes_changed", "user.updated");
        a.put("user.identity_unlinked", "user.updated");
        a.put("user.signed_out_everywhere", "session.revoked");
        a.put("user.signed_out_of_application", "session.revoked");
        a.put("user.application_access_granted", "user.application_access_granted");
        a.put("user.application_access_revoked", "user.application_access_revoked");
        a.put("session.revoked", "session.revoked");
        a.put("role.created", "role.created");
        a.put("role.updated", "role.updated");
        a.put("role.permission.assigned", "role.updated");
        a.put("role.permission.removed", "role.updated");
        a.put("role.deleted", "role.deleted");
        a.put("group.created", "group.changed");
        a.put("group.updated", "group.changed");
        a.put("group.deleted", "group.changed");
        a.put("account.deletion_requested", "account.deletion_requested");
        FROM_AUDIT = java.util.Collections.unmodifiableMap(a);
    }

    /** "*" plus every catalogued event. */
    public static Set<String> subscribable() {
        Set<String> out = new java.util.LinkedHashSet<>();
        out.add("*");
        out.addAll(CATALOG.keySet());
        return out;
    }
}
