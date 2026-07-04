package com.secureone.auth.notify;

public interface SmsProvider {

    void send(String toE164, String message);

    boolean isConfigured();
}
