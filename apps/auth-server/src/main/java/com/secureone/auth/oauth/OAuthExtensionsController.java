package com.secureone.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2")
public class OAuthExtensionsController {

    private static final Duration PAR_TTL = Duration.ofMinutes(5);
    private static final int PAR_EXPIRES_IN_SECONDS = 300;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OAuthExtensionsController(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/par")
    public Map<String, Object> pushedAuthorizationRequest(@RequestBody Map<String, Object> body) {
        String requestUri = "urn:secureone:par:" + UUID.randomUUID();
        try {
            String payload = objectMapper.writeValueAsString(body != null ? body : Map.of());
            redis.opsForValue().set(parKey(requestUri), payload, PAR_TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store PAR request", ex);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_uri", requestUri);
        out.put("expires_in", PAR_EXPIRES_IN_SECONDS);
        return out;
    }

    @PostMapping("/backchannel-logout")
    public ResponseEntity<Void> backchannelLogout(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok().build();
    }

    private static String parKey(String requestUri) {
        return "secureone:par:" + Base64.getUrlEncoder().withoutPadding().encodeToString(requestUri.getBytes());
    }
}
