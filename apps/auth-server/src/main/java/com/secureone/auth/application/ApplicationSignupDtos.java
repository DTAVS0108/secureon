package com.secureone.auth.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public final class ApplicationSignupDtos {

    private ApplicationSignupDtos() {}

    /**
     * An email and password, or (when the application allows phone sign-up) a phone number alone: the
     * password is then optional and the number is verified with a text-message code.
     */
    public record SignupRequest(
            @Email @Size(max = 320) String email,
            @Size(min = 8, max = 128) String password,
            String phone,
            String firstName,
            String lastName,
            String displayName,
            String verificationMode,
            String verificationChannel,
            /** Values of the application's extra sign-up fields, by key. */
            java.util.Map<String, Object> attributes) {}
}
