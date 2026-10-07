package com.secureone.auth.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RlsScopeTest {

    @Test
    void platformScopeAppliesOnlyInsideTheBlock() {
        assertFalse(RlsScope.isPlatform());
        assertEquals("ok", RlsScope.asPlatform(() -> {
            assertTrue(RlsScope.isPlatform());
            return "ok";
        }));
        assertFalse(RlsScope.isPlatform());
    }

    @Test
    void nestedBlocksKeepScopeUntilTheOutermostEnds() {
        RlsScope.runAsPlatform(() -> {
            RlsScope.runAsPlatform(() -> assertTrue(RlsScope.isPlatform()));
            assertTrue(RlsScope.isPlatform());
        });
        assertFalse(RlsScope.isPlatform());
    }

    @Test
    void scopeIsClearedWhenTheBlockThrows() {
        assertThrows(IllegalStateException.class, () -> RlsScope.runAsPlatform(() -> {
            throw new IllegalStateException("boom");
        }));
        assertFalse(RlsScope.isPlatform());
    }
}
