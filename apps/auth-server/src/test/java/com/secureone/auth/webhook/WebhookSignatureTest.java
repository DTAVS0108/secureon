package com.secureone.auth.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theTimestampIsSignedWithTheBody() throws Exception {
        String header = WebhookDeliveryService.signatureHeader(1700000000L, "{\"a\":1}", List.of("s1"));
        assertThat(header).isEqualTo("t=1700000000,v1=" + hmac("1700000000.{\"a\":1}", "s1"));
    }

    @Test
    void duringARotationBothSecretsSign() throws Exception {
        String header = WebhookDeliveryService.signatureHeader(5L, "b", List.of("new", "old"));
        assertThat(header).isEqualTo("t=5,v1=" + hmac("5.b", "new") + ",v1=" + hmac("5.b", "old"));
    }

    @Test
    void retriesBackOffOverAboutTwoDays() {
        assertThat(WebhookDeliveryService.MAX_ATTEMPTS).isEqualTo(8);
        assertThat(WebhookDeliveryService.BACKOFF).hasSize(WebhookDeliveryService.MAX_ATTEMPTS - 1);
        long totalHours = WebhookDeliveryService.BACKOFF.stream().mapToLong(d -> d.toMinutes()).sum() / 60;
        assertThat(totalHours).isBetween(40L, 50L);
    }
}
