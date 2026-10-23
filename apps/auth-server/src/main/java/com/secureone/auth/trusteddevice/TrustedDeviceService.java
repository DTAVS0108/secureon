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

/**
 * "Don't ask again on this device". A device is remembered in one application (each application decides
 * whether and for how long), with its own cookie, so a device remembered in one application never skips the
 * second step in another.
 */
@Service
public class TrustedDeviceService {

    /** Cookie for sign-ins without an application; applications use {@link #cookieName(UUID)}. */
    public static final String COOKIE_NAME = "secureone_trust";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TrustedDeviceRepository devices;

    public TrustedDeviceService(TrustedDeviceRepository devices) {
        this.devices = devices;
    }

    static String cookieName(UUID applicationId) {
        return applicationId == null ? COOKIE_NAME : COOKIE_NAME + "_" + applicationId.toString().replace("-", "");
    }

    @Transactional
    public void issue(UUID userId, UUID applicationId, int rememberDeviceDays,
            HttpServletRequest request, HttpServletResponse response) {
        if (rememberDeviceDays <= 0 || userId == null) {
            return;
        }
        String rawToken = generateToken();
        TrustedDevice device = new TrustedDevice();
        device.setUserId(userId);
        device.setApplicationId(applicationId);
        device.setDeviceTokenHash(ServiceAccountService.hashKey(rawToken));
        device.setLabel(trimUserAgent(request != null ? request.getHeader(HttpHeaders.USER_AGENT) : null));
        device.setExpiresAt(Instant.now().plus(Duration.ofDays(rememberDeviceDays)));
        devices.save(device);

        ResponseCookie cookie = ResponseCookie.from(cookieName(applicationId), rawToken)
                .httpOnly(true)
                .secure(request != null && request.isSecure())
                .path("/")
                .maxAge(Duration.ofDays(rememberDeviceDays))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Whether this browser was remembered in this application, within the days the application allows now
     * ({@code rememberDeviceDays}: an application that lowered it, or turned it off, is not skipped).
     */
    @Transactional
    public boolean isTrusted(UUID userId, UUID applicationId, int rememberDeviceDays, HttpServletRequest request) {
        if (userId == null || request == null || rememberDeviceDays <= 0) {
            return false;
        }
        String rawToken = readCookie(request, cookieName(applicationId));
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        return devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken))
                .filter(device -> java.util.Objects.equals(device.getApplicationId(), applicationId))
                .filter(device -> device.getExpiresAt().isAfter(now))
                .filter(device -> device.getCreatedAt().plus(Duration.ofDays(rememberDeviceDays)).isAfter(now))
                .map(device -> {
                    device.setLastUsedAt(Instant.now());
                    devices.save(device);
                    return true;
                })
                .orElse(false);
    }

    /** Devices that skip the second step, newest first (never the token). */
    @Transactional(readOnly = true)
    public java.util.List<TrustedDevice> list(UUID userId) {
        return devices.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(device -> device.getExpiresAt().isAfter(Instant.now()))
                .toList();
    }

    /** The user forgets one device; false when it is not theirs. */
    @Transactional
    public boolean revoke(UUID userId, UUID deviceId) {
        return devices.findByIdAndUserId(deviceId, userId).map(device -> {
            devices.delete(device);
            return true;
        }).orElse(false);
    }

    /** Forgets every device: after a reset, a recovery, a password reset or a removed method. */
    @Transactional
    public long revokeAll(UUID userId) {
        return userId != null ? devices.deleteByUserId(userId) : 0;
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
