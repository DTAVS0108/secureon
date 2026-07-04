package com.secureone.auth.notify.sms;

/** Decrypted credentials for outbound SMS (never exposed via admin API). */
public record SmsResolvedCredentials(
        String provider,
        boolean enabled,
        String twilioAccountSid,
        String twilioAuthToken,
        String twilioFromNumber,
        String awsRegion,
        String awsAccessKeyId,
        String awsSecretAccessKey,
        String awsSenderId,
        String msg91ApiKey,
        String msg91SenderId,
        String msg91TemplateId) {

    public static SmsResolvedCredentials logOnly() {
        return new SmsResolvedCredentials(SmsProviderIds.LOG, true, null, null, null, null, null, null, null, null, null, null);
    }
}
