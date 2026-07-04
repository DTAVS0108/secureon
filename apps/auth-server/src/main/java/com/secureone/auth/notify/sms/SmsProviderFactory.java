package com.secureone.auth.notify.sms;

import com.secureone.auth.notify.AwsSnsSmsProvider;
import com.secureone.auth.notify.LoggingSmsProvider;
import com.secureone.auth.notify.Msg91SmsProvider;
import com.secureone.auth.notify.SmsProvider;
import com.secureone.auth.notify.TwilioSmsProvider;
import org.springframework.stereotype.Component;

@Component
public class SmsProviderFactory {

    private final LoggingSmsProvider loggingProvider;

    public SmsProviderFactory(LoggingSmsProvider loggingProvider) {
        this.loggingProvider = loggingProvider;
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
                        credentials.twilioFromNumber());
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
                        credentials.awsSenderId());
            }
            case SmsProviderIds.MSG91 -> {
                if (isBlank(credentials.msg91ApiKey())) {
                    yield loggingProvider;
                }
                yield new Msg91SmsProvider(
                        credentials.msg91ApiKey(), credentials.msg91SenderId(), credentials.msg91TemplateId());
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
            case SmsProviderIds.MSG91 -> !isBlank(credentials.msg91ApiKey());
            default -> false;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
