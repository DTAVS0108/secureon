package com.secureone.auth.config;

import org.springframework.security.authentication.InternalAuthenticationServiceException;

/**
 * Every password-hashing slot stayed busy for the whole wait ({@link AppConfig}): the server is overloaded,
 * not the credentials wrong. Answered with 503 and Retry-After, and never counted as a failed sign-in.
 */
public class PasswordHashingBusyException extends InternalAuthenticationServiceException {

    public static final int RETRY_AFTER_SECONDS = 2;

    public PasswordHashingBusyException() {
        super("Too many sign-ins at once. Try again in a moment.");
    }
}
