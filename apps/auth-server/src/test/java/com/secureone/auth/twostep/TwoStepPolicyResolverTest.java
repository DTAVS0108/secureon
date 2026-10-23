package com.secureone.auth.twostep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.SettingsExposureService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * An application's own two-factor settings apply only while it has them: Authentication is open to applications
 * and its "Two Factor Authentication" switch (Feature flags) is on. Otherwise platform and tenant settings apply.
 */
class TwoStepPolicyResolverTest {

    private final UUID app = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final PlatformSettingsService platformSettings = mock(PlatformSettingsService.class);
    private final TenantSettings tenantSettings = mock(TenantSettings.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SettingsExposureService exposure = mock(SettingsExposureService.class);
    private final Map<String, Map<String, Object>> appSettings = new HashMap<>();
    private TwoStepPolicyResolver resolver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(platformSettings.get(TwoStepPolicyResolver.KEY)).thenReturn(Map.of("mode", "optional"));
        when(tenantSettings.get(tenant, TwoStepPolicyResolver.KEY)).thenReturn(Map.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("tenant_id FROM platform.application")) {
                return List.of(tenant);
            }
            if (sql.contains("schema_name")) {
                return List.of("");
            }
            String key = (String) call.getArguments()[call.getArguments().length - 1];
            Map<String, Object> value = appSettings.get(key);
            return value == null ? List.of() : List.of(value);
        });
        resolver = new TwoStepPolicyResolver(platformSettings, tenantSettings, jdbc, new ObjectMapper(), tx, exposure);
        appSettings.put(TwoStepPolicyResolver.KEY, Map.of("mode", "required"));
    }

    private void switchOn(boolean on) {
        appSettings.put(TwoStepPolicyResolver.OWN_SETTINGS_KEY, Map.of("tabEnabled", on));
    }

    @Test
    void ownSettingsApplyWhileOpenAndSwitchedOn() {
        when(exposure.isExposed("two-factor")).thenReturn(true);
        switchOn(true);

        assertTrue(resolver.hasOwnSettings(app));
        assertEquals("required", resolver.resolve(app).policy().get("mode"));
    }

    @Test
    void switchOffMeansPlatformAndTenantSettings() {
        when(exposure.isExposed("two-factor")).thenReturn(true);
        switchOn(false);

        assertFalse(resolver.hasOwnSettings(app));
        assertEquals("optional", resolver.resolve(app).policy().get("mode"), "saved values wait unused");
    }

    @Test
    void notOpenToApplicationsMeansPlatformAndTenantSettings() {
        when(exposure.isExposed("two-factor")).thenReturn(false);
        switchOn(true);

        assertFalse(resolver.hasOwnSettings(app));
        assertEquals("optional", resolver.resolve(app).policy().get("mode"));
    }

    @Test
    void backupCodeCountIsNeverAnApplicationSetting() {
        when(exposure.isExposed("two-factor")).thenReturn(true);
        switchOn(true);
        appSettings.put(TwoStepPolicyResolver.KEY, Map.of("recoveryCodesPerEnrollment", 3));

        assertEquals(10, resolver.resolve(app).policy().get("recoveryCodesPerEnrollment"), "backup codes are shared");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> resolver.requireAllowedForApplication(app, Map.of("recoveryCodesPerEnrollment", 3)));
    }

    @Test
    void platformSignInHasNoApplicationSettings() {
        assertFalse(resolver.hasOwnSettings(null));
        assertEquals("optional", resolver.resolve(null).policy().get("mode"));
    }
}
