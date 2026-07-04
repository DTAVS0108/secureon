package com.secureone.auth.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev/default SMS provider — logs OTP to console. */
@Component
public class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    @Override
    public void send(String toE164, String message) {
        log.info("SMS to {}: {}", toE164, message);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}
