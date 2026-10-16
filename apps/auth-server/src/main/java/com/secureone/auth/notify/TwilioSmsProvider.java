package com.secureone.auth.notify;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Twilio Programmable Messaging over its REST API, with this provider's own account. (The Twilio SDK's
 * {@code Twilio.init} sets one account for the whole JVM: with per-application SMS settings the last
 * app configured would send, and be billed, for every app.)
 */
public class TwilioSmsProvider implements SmsProvider {

    private static final RestClient HTTP = RestClient.create();

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String apiBaseUrl;

    public TwilioSmsProvider(String accountSid, String authToken, String fromNumber, String apiBaseUrl) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    @Override
    public void send(String toE164, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add("From", fromNumber);
        form.add("Body", message);
        String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        HTTP.post()
                .uri(apiBaseUrl + "/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean isConfigured() {
        return fromNumber != null && !fromNumber.isBlank();
    }
}
