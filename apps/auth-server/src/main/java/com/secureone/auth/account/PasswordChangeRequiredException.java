package com.secureone.auth.account;

import org.springframework.security.authentication.CredentialsExpiredException;

/**
 * The password was right but is temporary (set by an administrator): the user is not signed in, and instead
 * chooses their own password with the one-time {@link #changeToken()} on the reset-password page.
 */
public class PasswordChangeRequiredException extends CredentialsExpiredException {

    public static final String MESSAGE = "Choose a new password to finish signing in.";

    private final String changeToken;

    public PasswordChangeRequiredException(String changeToken) {
        super(MESSAGE);
        this.changeToken = changeToken;
    }

    public String changeToken() {
        return changeToken;
    }
}
