package com.secureone.auth.notify;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class AwsSnsSmsProvider implements SmsProvider {

    private final SnsClient client;
    private final String senderId;

    public AwsSnsSmsProvider(String region, String accessKeyId, String secretAccessKey, String senderId) {
        this.senderId = senderId != null ? senderId : "";
        this.client = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    @Override
    public void send(String toE164, String message) {
        PublishRequest.Builder request =
                PublishRequest.builder().phoneNumber(toE164).message(message);
        if (!senderId.isBlank()) {
            request = request.messageAttributes(java.util.Map.of(
                    "AWS.SNS.SMS.SenderID",
                    software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(senderId)
                            .build()));
        }
        client.publish(request.build());
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }
}
