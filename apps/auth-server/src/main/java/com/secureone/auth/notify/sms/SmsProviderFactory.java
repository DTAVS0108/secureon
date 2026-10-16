package com.secureone.auth.notify.sms;

import com.secureone.auth.notify.AwsSnsSmsProvider;
import com.secureone.auth.notify.LoggingSmsProvider;
import com.secureone.auth.notify.Msg91SmsProvider;
import com.secureone.auth.notify.SmsProvider;
import com.secureone.auth.notify.TwilioSmsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SmsProviderFactory {

    private final LoggingSmsProvider loggingProvider;
    private final String twilioApiBaseUrl;
    private final String msg91ApiBaseUrl;
    private final String awsSnsEndpoint;

    /**
     * Gateway base URLs are operator settings (regional endpoints, or a local stand-in for tests), not
     * per-application fields: an app admin must not be able to send provider credentials to another host.
     */
    public SmsProviderFactory(
            LoggingSmsProvider loggingProvider,
            @Value("${secureone.sms.twilio.api-base-url:https://api.twilio.com}") String twilioApiBaseUrl,
            @Value("${secureone.sms.msg91.api-base-url:https://control.msg91.com}") String msg91ApiBaseUrl,
            @Value("${secureone.sms.aws-sns.endpoint:}") String awsSnsEndpoint) {
        this.loggingProvider = loggingProvider;
        this.twilioApiBaseUrl = twilioApiBaseUrl;
        this.msg91ApiBaseUrl = msg91ApiBaseUrl;
        this.awsSnsEndpoint = awsSnsEndpoint;
    }

    public SmsProvider create(SmsResolvedCredentials credentials) {
        if (credentials == null || !credentials.enabled()) {
            return loggingProvider;
        }
        return switch (credentials.provider()) {
            case SmsProviderIds.TWILIO -> {
                if (isBlank(credentials.twilioAccountSid())
                        || isBlank(credentials.twilioAuthToken())
                        || isBlank(credentials.twilioFromNumber())) {
                    yield loggingProvider;
                }
                yield new TwilioSmsProvider(
                        credentials.twilioAccountSid(),
                        credentials.twilioAuthToken(),
                        credentials.twilioFromNumber(),
                        twilioApiBaseUrl);
            }
            case SmsProviderIds.AWS_SNS -> {
                if (isBlank(credentials.awsRegion())
                        || isBlank(credentials.awsAccessKeyId())
                        || isBlank(credentials.awsSecretAccessKey())) {
                    yield loggingProvider;
                }
                yield new AwsSnsSmsProvider(
                        credentials.awsRegion(),
                        credentials.awsAccessKeyId(),
                        credentials.awsSecretAccessKey(),
                        credentials.awsSenderId(),
                        awsSnsEndpoint);
            }
            case SmsProviderIds.MSG91 -> {
                if (isBlank(credentials.msg91ApiKey()) || isBlank(credentials.msg91TemplateId())) {
                    yield loggingProvider;
                }
                yield new Msg91SmsProvider(
                        credentials.msg91ApiKey(),
                        credentials.msg91SenderId(),
                        credentials.msg91TemplateId(),
                        msg91ApiBaseUrl);
            }
            default -> loggingProvider;
        };
    }

    public boolean isConfigured(SmsResolvedCredentials credentials) {
        if (credentials == null || !credentials.enabled()) {
            return loggingProvider.isConfigured();
        }
        return switch (credentials.provider()) {
            case SmsProviderIds.LOG -> loggingProvider.isConfigured();
            case SmsProviderIds.TWILIO -> !isBlank(credentials.twilioAccountSid())
                    && !isBlank(credentials.twilioAuthToken())
                    && !isBlank(credentials.twilioFromNumber());
            case SmsProviderIds.AWS_SNS -> !isBlank(credentials.awsRegion())
                    && !isBlank(credentials.awsAccessKeyId())
                    && !isBlank(credentials.awsSecretAccessKey());
            // The Flow API needs a DLT template; without one nothing can be sent.
            case SmsProviderIds.MSG91 -> !isBlank(credentials.msg91ApiKey()) && !isBlank(credentials.msg91TemplateId());
            default -> false;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
