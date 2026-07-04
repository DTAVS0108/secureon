package com.secureone.auth.trusteddevice;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

@ExtendWith(MockitoExtension.class)
class TrustedDeviceServiceTest {

    @Mock
    private TrustedDeviceRepository devices;

    private TrustedDeviceService service;

    @BeforeEach
    void setUp() {
        service = new TrustedDeviceService(devices);
    }

    @Test
    void issueSetsSecureCookieAndPersistsDevice() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "JUnit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(devices.save(any(TrustedDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(userId, 30, request, response);

        ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
        verify(devices).save(captor.capture());
        assertTrue(captor.getValue().getExpiresAt().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
        assertTrue(response.getHeader("Set-Cookie").contains(TrustedDeviceService.COOKIE_NAME + "="));
    }

    @Test
    void isTrustedReturnsTrueForValidCookie() {
        UUID userId = UUID.randomUUID();
        String rawToken = "device-token-value";
        TrustedDevice device = new TrustedDevice();
        device.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(TrustedDeviceService.COOKIE_NAME, rawToken));

        when(devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken)))
                .thenReturn(Optional.of(device));
        when(devices.save(device)).thenReturn(device);

        assertTrue(service.isTrusted(userId, request));
        verify(devices).save(device);
    }

    @Test
    void isTrustedReturnsFalseForExpiredDevice() {
        UUID userId = UUID.randomUUID();
        String rawToken = "expired-token";
        TrustedDevice device = new TrustedDevice();
        device.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(TrustedDeviceService.COOKIE_NAME, rawToken));

        when(devices.findByUserIdAndDeviceTokenHash(userId, ServiceAccountService.hashKey(rawToken)))
                .thenReturn(Optional.of(device));

        assertFalse(service.isTrusted(userId, request));
    }

    @Test
    void issueSkipsWhenRememberDaysZero() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.issue(UUID.randomUUID(), 0, new MockHttpServletRequest(), response);
        verify(devices, org.mockito.Mockito.never()).save(any());
        assertFalse(response.containsHeader("Set-Cookie"));
    }
}
