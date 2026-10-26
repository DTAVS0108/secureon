package com.secureone.auth.admin.audit;

/** Test access to package-private helpers. */
public final class AuditQueryServiceAccess {
    private AuditQueryServiceAccess() {}

    public static String csv(String value) {
        return AuditQueryService.csv(value);
    }
}
