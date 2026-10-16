package com.secureone.auth.notify;

public interface SmsProvider {

    void send(String toE164, String message);

    /**
     * A one-time code. Template-based gateways (MSG91 Flow, required for India DLT) put {@code code} in
     * the template's variable; the others send {@code message} as is.
     */
    default void sendOtp(String toE164, String code, String message) {
        send(toE164, message);
    }

    boolean isConfigured();
}
