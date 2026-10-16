package com.secureone.auth.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * MSG91 Flow API (v5): {@code {template_id, recipients: [{mobiles, <template variables>}]}} with the
 * {@code authkey} header. Sending in India needs a DLT-approved template, so the template id is required.
 * The template should use {@code ##otp##} for the code; {@code ##message##} carries the full text for
 * templates that show it (and for the "Send test" message).
 */
public class Msg91SmsProvider implements SmsProvider {

    public static final String OTP_VARIABLE = "otp";
    public static final String MESSAGE_VARIABLE = "message";

    private static final RestClient HTTP = RestClient.create();

    private final String apiKey;
    private final String senderId;
    private final String templateId;
    private final String apiBaseUrl;

    public Msg91SmsProvider(String apiKey, String senderId, String templateId, String apiBaseUrl) {
        this.apiKey = apiKey;
        this.senderId = senderId != null ? senderId : "";
        this.templateId = templateId != null ? templateId : "";
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    @Override
    public void send(String toE164, String message) {
        post(toE164, null, message);
    }

    @Override
    public void sendOtp(String toE164, String code, String message) {
        post(toE164, code, message);
    }

    private void post(String toE164, String code, String message) {
        Map<String, Object> recipient = new LinkedHashMap<>();
        // International format without "+", e.g. 919876543210.
        recipient.put("mobiles", toE164.startsWith("+") ? toE164.substring(1) : toE164);
        if (code != null) {
            recipient.put(OTP_VARIABLE, code);
        }
        recipient.put(MESSAGE_VARIABLE, message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId);
        body.put("short_url", "0");
        if (!senderId.isBlank()) {
            body.put("sender", senderId);
        }
        body.put("recipients", List.of(recipient));
        HTTP.post()
                .uri(apiBaseUrl + "/api/v5/flow")
                .header("authkey", apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !templateId.isBlank();
    }
}
