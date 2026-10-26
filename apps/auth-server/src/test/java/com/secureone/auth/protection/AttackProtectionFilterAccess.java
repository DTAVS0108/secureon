package com.secureone.auth.protection;

/** Test access to package-private helpers. */
public final class AttackProtectionFilterAccess {
    private AttackProtectionFilterAccess() {}

    public static String action(String uri) {
        return AttackProtectionFilter.action(uri);
    }
}
