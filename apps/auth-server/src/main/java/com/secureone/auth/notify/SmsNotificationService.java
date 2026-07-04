package com.secureone.auth.notify;

import com.secureone.auth.notify.sms.ApplicationSmsResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SmsNotificationService {

    private final ApplicationSmsResolver smsResolver;

    public SmsNotificationService(ApplicationSmsResolver smsResolver) {
        this.smsResolver = smsResolver;
    }

    public void sendOtp(UUID applicationId, String toE164, String code, String purposeLabel) {
        String message = "Your SecureOne " + purposeLabel + " code is: " + code + ". It expires in 10 minutes.";
        smsResolver.resolveProvider(applicationId).send(toE164, message);
    }

    public void sendTest(UUID applicationId, String toE164) {
        String message = "SecureOne SMS test — if you received this, your provider is configured correctly.";
        smsResolver.resolveProvider(applicationId).send(toE164, message);
    }

    public boolean isConfigured(UUID applicationId) {
        return smsResolver.isConfigured(applicationId);
    }
}
