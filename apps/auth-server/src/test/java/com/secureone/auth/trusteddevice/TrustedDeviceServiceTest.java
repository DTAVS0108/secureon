package com.secureone.auth.trusteddevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.serviceaccount.ServiceAccountService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** "Don't ask again on this device": per application, within the days that application allows now. */
@ExtendWith(MockitoExtension.class)
class TrustedDeviceServiceTest {

    @Mock
    private TrustedDeviceRepository devices;

    private TrustedDeviceService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID appA = UUID.randomUUID();
    private final UUID appB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TrustedDeviceService(devices);
    }

    private static TrustedDevice device(UUID applicationId, Instant createdAt, Instant expiresAt) {
        TrustedDevice device = new TrustedDevice();
        device.setApplicationId(applicationId);
        device.setCreatedAt(createdAt);
        device.setExpiresAt(expiresAt);
        return device;
    }

    private static MockHttpServletRequest browserWith(String cookie, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(cookie, token));
        return request;
    }

    @Test
    void issueSetsSecureCookieAndPersistsDevice() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "JUnit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(devices.save(any(TrustedDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(userId, appA, 30, request, response);

        ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
        verify(devices).save(captor.capture());
        assertTrue(captor.getValue().getExpiresAt().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
        assertEquals(appA, captor.getValue().getApplicationId(), "remembered in this application");
        assertTrue(response.getHeader("Set-Cookie").contains(TrustedDeviceService.cookieName(appA) + "="));
    }

    @Test
    void isTrustedReturnsTrueForValidCookie() {
        String rawToken = "device-token-value";
        TrustedDevice device = device(appA, Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        when(devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken)))
                .thenReturn(Optional.of(device));
        when(devices.save(device)).thenReturn(device);

        assertTrue(service.isTrusted(userId, appA, 30, browserWith(TrustedDeviceService.cookieName(appA), rawToken)));
        verify(devices).save(device);
    }

    @Test
    void isTrustedReturnsFalseForExpiredDevice() {
        String rawToken = "expired-token";
        TrustedDevice device = device(appA, Instant.now().minus(31, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.HOURS));
        when(devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken)))
                .thenReturn(Optional.of(device));

        assertFalse(service.isTrusted(userId, appA, 30, browserWith(TrustedDeviceService.cookieName(appA), rawToken)));
    }

    @Test
    void issueSkipsWhenRememberDaysZero() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.issue(userId, appA, 0, new MockHttpServletRequest(), response);
        verify(devices, org.mockito.Mockito.never()).save(any());
        assertFalse(response.containsHeader("Set-Cookie"));
    }

    @Test
    void eachApplicationHasItsOwnCookie() {
        assertNotEquals(TrustedDeviceService.cookieName(appA), TrustedDeviceService.cookieName(appB));
        assertEquals(TrustedDeviceService.COOKIE_NAME, TrustedDeviceService.cookieName(null), "no application");
    }

    @Test
    void notTrustedInAnotherApplication() {
        // Even when the token is presented under the other application's cookie name.
        String rawToken = "app-a-token";
        TrustedDevice device = device(appA, Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));
        when(devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken)))
                .thenReturn(Optional.of(device));

        assertFalse(service.isTrusted(userId, appB, 30, browserWith(TrustedDeviceService.cookieName(appB), rawToken)));
    }

    @Test
    void notTrustedOnceTheApplicationAllowsFewerDays() {
        String rawToken = "old-token";
        TrustedDevice device = device(appA, Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));
        when(devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken)))
                .thenReturn(Optional.of(device));

        assertFalse(service.isTrusted(userId, appA, 7, browserWith(TrustedDeviceService.cookieName(appA), rawToken)),
                "remembered 10 days ago, the application now allows 7");
        assertFalse(service.isTrusted(userId, appA, 0, browserWith(TrustedDeviceService.cookieName(appA), rawToken)),
                "the application turned remembering off");
    }
}
