package com.secureone.auth.account.otp;

/** Which verification_policy section supplies OTP timing and attempt limits. */
public enum OtpPolicySection {
    SIGNUP("signup"),
    LOGIN("login"),
    RECOVERY("recovery");

    private final String key;

    OtpPolicySection(String key) {
        this.key = key;
    }

    public String policyKey() {
        return key;
    }
}
