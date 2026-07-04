package com.secureone.auth.notify;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** MSG91 transactional SMS (India-friendly). */
public class Msg91SmsProvider implements SmsProvider {

    private static final RestClient HTTP = RestClient.create();

    private final String apiKey;
    private final String senderId;
    private final String templateId;

    public Msg91SmsProvider(String apiKey, String senderId, String templateId) {
        this.apiKey = apiKey;
        this.senderId = senderId != null ? senderId : "";
        this.templateId = templateId != null ? templateId : "";
    }

    @Override
    public void send(String toE164, String message) {
        String mobiles = toE164.startsWith("+") ? toE164.substring(1) : toE164;
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("sender", senderId.isBlank() ? "SECURE" : senderId);
        body.put("route", "4");
        body.put("country", "0");
        body.put("sms", java.util.List.of(java.util.Map.of("message", message, "to", java.util.List.of(mobiles))));
        if (!templateId.isBlank()) {
            body.put("DLTTemplateId", templateId);
        }
        HTTP.post()
                .uri("https://control.msg91.com/api/v5/flow/")
                .header("authkey", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
