package com.secureone.auth.notify;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

public class TwilioSmsProvider implements SmsProvider {

    private final String fromNumber;

    public TwilioSmsProvider(String accountSid, String authToken, String fromNumber) {
        Twilio.init(accountSid, authToken);
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(String toE164, String message) {
        Message.creator(new PhoneNumber(toE164), new PhoneNumber(fromNumber), message).create();
    }

    @Override
    public boolean isConfigured() {
        return fromNumber != null && !fromNumber.isBlank();
    }
}
