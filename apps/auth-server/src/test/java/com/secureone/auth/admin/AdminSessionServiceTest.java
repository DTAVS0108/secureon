package com.secureone.auth.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.secureone.auth.authn.AdminSessionPrincipals;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminSessionServiceTest {

    private static final Duration IDLE = Duration.ofMinutes(30);
    private static final Duration MAX = Duration.ofHours(8);
    private static final Duration BREAK_GLASS_MAX = Duration.ofHours(1);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private AdminSessionPrincipals principals;

    private AdminSessionService sessions;

    @BeforeEach
    void setUp() {
        sessions = new AdminSessionService(redis, principals, IDLE, MAX, BREAK_GLASS_MAX);
    }

    private static String stored(Instant expiresAt, String principal) {
        return expiresAt.getEpochSecond() + "|" + principal;
    }

    @Test
    void createStoresOnlyAHashOfTheTokenWithTheIdleTtl() {
        when(redis.opsForValue()).thenReturn(values);

        var issued = sessions.create(
                UsernamePasswordAuthenticationToken.authenticated("acme:jane@acme.test", null, List.of()), false);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), value.capture(), eq(IDLE));
        assertEquals(43, issued.token().length());
        assertFalse(key.getValue().contains(issued.token()));
        assertEquals(AdminSessionService.key(issued.token()), key.getValue());
        assertTrue(value.getValue().endsWith("|acme:jane@acme.test"));
        assertEquals(MAX.toSeconds(), issued.expiresIn());
        assertEquals(IDLE.toSeconds(), issued.idleTimeout());
    }

    @Test
    void breakGlassSessionsGetTheShorterLifetime() {
        when(redis.opsForValue()).thenReturn(values);

        var issued = sessions.create(UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of()), true);

        assertEquals(BREAK_GLASS_MAX.toSeconds(), issued.expiresIn());
    }

    @Test
    void tokensAreUnique() {
        when(redis.opsForValue()).thenReturn(values);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of());

        assertNotEquals(sessions.create(auth, false).token(), sessions.create(auth, false).token());
    }

    @Test
    void malformedTokenNeverReachesRedis() {
        assertTrue(sessions.resolve("not a token").isEmpty());
        assertTrue(sessions.resolve(null).isEmpty());
        verifyNoInteractions(redis, principals);
    }

    @Test
    void unknownTokenResolvesToNothing() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        assertTrue(sessions.resolve("a".repeat(43)).isEmpty());
        verifyNoInteractions(principals);
    }

    @Test
    void knownTokenRestoresThePrincipalAndRestartsTheIdleTimer() {
        String token = "b".repeat(43);
        Authentication restored = UsernamePasswordAuthenticationToken.authenticated("acme:jane@acme.test", null, List.of());
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(stored(Instant.now().plus(MAX), "acme:jane@acme.test"));
        when(principals.restore("acme:jane@acme.test")).thenReturn(Optional.of(restored));

        assertEquals(Optional.of(restored), sessions.resolve(token));
        verify(redis, never()).delete(anyString());
        verify(redis).expire(AdminSessionService.key(token), IDLE);
    }

    @Test
    void sessionPastItsAbsoluteLifetimeIsDeleted() {
        String token = "d".repeat(43);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(stored(Instant.now().minusSeconds(5), "acme:jane@acme.test"));

        assertTrue(sessions.resolve(token).isEmpty());
        verify(redis).delete(AdminSessionService.key(token));
        verifyNoInteractions(principals);
    }

    @Test
    void tokenFromBeforeIdleTimeoutsIsDeleted() {
        String token = "e".repeat(43);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("acme:jane@acme.test");

        assertTrue(sessions.resolve(token).isEmpty());
        verify(redis).delete(AdminSessionService.key(token));
    }

    @Test
    void sessionForADisabledAccountIsDeleted() {
        String token = "c".repeat(43);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(stored(Instant.now().plus(MAX), "acme:gone@acme.test"));
        when(principals.restore("acme:gone@acme.test")).thenReturn(Optional.empty());

        assertTrue(sessions.resolve(token).isEmpty());
        verify(redis).delete(AdminSessionService.key(token));
    }
}
