package com.secureone.auth.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ApplicationSignupDtos {

    private ApplicationSignupDtos() {}

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            String phone,
            String firstName,
            String lastName,
            String displayName,
            String verificationMode,
            String verificationChannel,
            /** Values of the application's extra sign-up fields, by key. */
            java.util.Map<String, Object> attributes) {}
}
