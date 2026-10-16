package com.secureone.auth.notify;

import com.secureone.auth.application.ApplicationDisplayName;
import com.secureone.auth.notify.sms.ApplicationSmsResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SmsNotificationService {

    private final ApplicationSmsResolver smsResolver;
    private final ApplicationDisplayName displayNames;

    public SmsNotificationService(ApplicationSmsResolver smsResolver, ApplicationDisplayName displayNames) {
        this.smsResolver = smsResolver;
        this.displayNames = displayNames;
    }

    /** Named after the app the user is using; no expiry claim (each policy section has its own lifetime). */
    public void sendOtp(UUID applicationId, String toE164, String code, String purposeLabel) {
        String message = code + " is your " + displayNames.of(applicationId) + " " + purposeLabel
                + " code. Do not share it with anyone.";
        smsResolver.resolveProvider(applicationId).sendOtp(toE164, code, message);
    }

    public void sendTest(UUID applicationId, String toE164) {
        String message = displayNames.of(applicationId) + " SMS test: your provider is configured correctly.";
        smsResolver.resolveProvider(applicationId).send(toE164, message);
    }

    public boolean isConfigured(UUID applicationId) {
        return smsResolver.isConfigured(applicationId);
    }
}
