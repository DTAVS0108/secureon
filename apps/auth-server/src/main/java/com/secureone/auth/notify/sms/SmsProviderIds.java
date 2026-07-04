package com.secureone.auth.notify.sms;

import java.util.List;

public final class SmsProviderIds {

    public static final String LOG = "log";
    public static final String TWILIO = "twilio";
    public static final String AWS_SNS = "aws_sns";
    public static final String MSG91 = "msg91";

    public static final List<String> ALL = List.of(LOG, TWILIO, AWS_SNS, MSG91);

    private SmsProviderIds() {}
}
