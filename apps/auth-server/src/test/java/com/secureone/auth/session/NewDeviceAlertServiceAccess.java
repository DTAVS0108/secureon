package com.secureone.auth.session;

/** Test access to package-private helpers. */
public final class NewDeviceAlertServiceAccess {
    private NewDeviceAlertServiceAccess() {}

    public static String describe(String userAgent) {
        return NewDeviceAlertService.describe(userAgent);
    }
}
