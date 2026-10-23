package com.secureone.auth.notify.sms;

import com.secureone.auth.notify.PlatformSmsSettingsService;
import com.secureone.auth.notify.SmsProvider;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationSmsResolver {

    private final ApplicationSmsSettingsService applicationSms;
    private final PlatformSmsSettingsService platformSms;
    private final SmsProviderFactory factory;

    public ApplicationSmsResolver(
            ApplicationSmsSettingsService applicationSms,
            PlatformSmsSettingsService platformSms,
            SmsProviderFactory factory) {
        this.applicationSms = applicationSms;
        this.platformSms = platformSms;
        this.factory = factory;
    }

    public SmsProvider resolveProvider(UUID applicationId) {
        SmsResolvedCredentials credentials = resolveCredentials(applicationId);
        if (!credentials.enabled()) {
            return factory.create(SmsResolvedCredentials.logOnly());
        }
        return factory.create(credentials);
    }

    public SmsResolvedCredentials resolveCredentials(UUID applicationId) {
        if (applicationId != null && applicationSms.hasOverride(applicationId)) {
            return applicationSms.resolveCredentials(applicationId);
        }
        return platformSms.resolveCredentials();
    }

    public boolean isConfigured(UUID applicationId) {
        return factory.isConfigured(resolveCredentials(applicationId));
    }

    /**
     * Text messages can really be sent: the SMS settings are turned on, with a usable provider. (Turned off, codes
     * only go to the server log, which is not something to offer users.)
     */
    public boolean canSend(UUID applicationId) {
        SmsResolvedCredentials credentials = resolveCredentials(applicationId);
        return credentials.enabled() && factory.isConfigured(credentials);
    }

    public boolean usesApplicationOverride(UUID applicationId) {
        return applicationId != null && applicationSms.hasOverride(applicationId);
    }
}
