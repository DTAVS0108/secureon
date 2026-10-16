package com.secureone.auth.notify;

import java.net.URI;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * AWS SNS direct SMS. Providers are resolved per message, so the client is built per send and closed
 * afterwards (an unclosed client per message leaks its connection pool and threads).
 */
public class AwsSnsSmsProvider implements SmsProvider {

    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String senderId;
    private final String endpointOverride;

    public AwsSnsSmsProvider(
            String region, String accessKeyId, String secretAccessKey, String senderId, String endpointOverride) {
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.senderId = senderId != null ? senderId : "";
        this.endpointOverride = endpointOverride;
    }

    @Override
    public void send(String toE164, String message) {
        PublishRequest.Builder request = PublishRequest.builder().phoneNumber(toE164).message(message);
        if (!senderId.isBlank()) {
            request = request.messageAttributes(Map.of(
                    "AWS.SNS.SMS.SenderID",
                    MessageAttributeValue.builder().dataType("String").stringValue(senderId).build()));
        }
        SnsClientBuilder builder = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpointOverride));
        }
        try (SnsClient client = builder.build()) {
            client.publish(request.build());
        }
    }

    @Override
    public boolean isConfigured() {
        return region != null && !region.isBlank();
    }
}
