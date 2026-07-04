package com.secureone.auth.trusteddevice;

import com.secureone.auth.serviceaccount.ServiceAccountService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustedDeviceService {

    public static final String COOKIE_NAME = "secureone_trust";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TrustedDeviceRepository devices;

    public TrustedDeviceService(TrustedDeviceRepository devices) {
        this.devices = devices;
    }

    @Transactional
    public void issue(UUID userId, int rememberDeviceDays, HttpServletRequest request, HttpServletResponse response) {
        if (rememberDeviceDays <= 0 || userId == null) {
            return;
        }
        String rawToken = generateToken();
        TrustedDevice device = new TrustedDevice();
        device.setUserId(userId);
        device.setDeviceTokenHash(ServiceAccountService.hashKey(rawToken));
        device.setLabel(trimUserAgent(request != null ? request.getHeader(HttpHeaders.USER_AGENT) : null));
        device.setExpiresAt(Instant.now().plus(Duration.ofDays(rememberDeviceDays)));
        devices.save(device);

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(request != null && request.isSecure())
                .path("/")
                .maxAge(Duration.ofDays(rememberDeviceDays))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional
    public boolean isTrusted(UUID userId, HttpServletRequest request) {
        if (userId == null || request == null) {
            return false;
        }
        String rawToken = readCookie(request, COOKIE_NAME);
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken))
                .filter(device -> device.getExpiresAt().isAfter(Instant.now()))
                .map(device -> {
                    device.setLastUsedAt(Instant.now());
                    devices.save(device);
                    return true;
                })
                .orElse(false);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String trimUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() > 150 ? trimmed.substring(0, 150) : trimmed;
    }
}
