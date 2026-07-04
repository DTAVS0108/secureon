package com.secureone.auth.account.otp;

import java.security.SecureRandom;

public final class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpGenerator() {}

    /** Cryptographically secure numeric OTP (default 6 digits). */
    public static String generate(int length) {
        if (length < 4 || length > 8) {
            throw new IllegalArgumentException("OTP length must be between 4 and 8");
        }
        int bound = (int) Math.pow(10, length);
        int floor = bound / 10;
        int code = floor + RANDOM.nextInt(bound - floor);
        return String.valueOf(code);
    }
}
