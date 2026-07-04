package com.secureone.auth.mfa.passkey;

final class WebAuthnErrors {

    private WebAuthnErrors() {}

    static RuntimeException wrap(Exception ex) {
        if (ex instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalArgumentException(ex.getMessage() != null ? ex.getMessage() : "WebAuthn operation failed.", ex);
    }

    static <T> T uncheck(WebAuthnCall<T> call) {
        try {
            return call.run();
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    @FunctionalInterface
    interface WebAuthnCall<T> {
        T run() throws Exception;
    }
}
