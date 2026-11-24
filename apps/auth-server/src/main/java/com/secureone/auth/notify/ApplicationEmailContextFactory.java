package com.secureone.auth.notify;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationDisplayName;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSetting;
import com.secureone.auth.application.ApplicationSettingRepository;
import com.secureone.auth.application.UserApplication;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.application.ApplicationNotificationDefaults;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationEmailContextFactory {

    private final ApplicationSettingRepository appSettings;
    private final UserApplicationRepository userApplications;
    private final ApplicationRepository applications;
    private final SmtpSettingsService smtpSettings;
    private final PlatformSmtpSettingsService platformSmtp;
    private final TenantRepository tenants;
    private final ApplicationDisplayName displayNames;

    public ApplicationEmailContextFactory(
            ApplicationSettingRepository appSettings,
            UserApplicationRepository userApplications,
            ApplicationRepository applications,
            SmtpSettingsService smtpSettings,
            PlatformSmtpSettingsService platformSmtp,
            TenantRepository tenants,
            ApplicationDisplayName displayNames) {
        this.tenants = tenants;
        this.displayNames = displayNames;
        this.appSettings = appSettings;
        this.userApplications = userApplications;
        this.applications = applications;
        this.smtpSettings = smtpSettings;
        this.platformSmtp = platformSmtp;
    }

    public UUID resolveApplicationId(UUID applicationId, UserAccount user) {
        if (applicationId != null) {
            return applicationId;
        }
        if (user == null) {
            return null;
        }
        // The applications the user belongs to: their memberships live in each application's own schema.
        java.util.List<UUID> memberOf = memberApplications(user);
        return memberOf.stream()
                .filter(smtpSettings::isConfigured)
                .findFirst()
                .orElseGet(() -> memberOf.stream()
                        .findFirst()
                        .orElseGet(() -> applications.findByTenantIdOrderByCreatedAtDesc(user.getTenantId()).stream()
                                .map(Application::getId)
                                .filter(smtpSettings::isConfigured)
                                .findFirst()
                                .orElseGet(() -> applications.findByTenantIdOrderByCreatedAtDesc(user.getTenantId()).stream()
                                        .map(Application::getId)
                                        .findFirst()
                                        .orElse(null))));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * The applications of the user's tenant the user is a member of, oldest first: memberships in the platform
     * schema (applications without their own schema) and in each application's own schema.
     */
    java.util.List<UUID> memberApplications(UserAccount user) {
        java.util.LinkedHashSet<UUID> out = new java.util.LinkedHashSet<>();
        userApplications.findByUserId(user.getId()).forEach(m -> out.add(m.getApplicationId()));
        if (jdbc == null) {
            return java.util.List.copyOf(out);
        }
        java.util.List<Application> apps = new java.util.ArrayList<>(applications.findByTenantIdOrderByCreatedAtDesc(user.getTenantId()));
        java.util.Collections.reverse(apps);
        for (Application app : apps) {
            String schema = app.getSchemaName();
            if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}") || out.contains(app.getId())) {
                continue;
            }
            try {
                Integer member = jdbc.queryForObject(
                        "SELECT count(*) FROM \"" + schema + "\".user_application WHERE user_id = ? AND application_id = ?",
                        Integer.class, user.getId(), app.getId());
                if (member != null && member > 0) {
                    out.add(app.getId());
                }
            } catch (RuntimeException e) {
                // A schema from an older layout: left out
            }
        }
        return java.util.List.copyOf(out);
    }

    public ApplicationEmailContext context(UUID applicationId) {
        if (applicationId == null) {
            throw new IllegalStateException("Application context is required for email delivery.");
        }
        Map<String, Object> notifications = mergedMap(applicationId, "notifications");
        Map<String, Object> email = mergedMap(applicationId, "email");
        Application app = applications.findById(applicationId).orElse(null);
        // End users of an integrated app (e.g. a shop) should see that app's name in the sender and the
        // template text, not the identity platform's default "SecureOne".
        String sender = String.valueOf(email.getOrDefault("fromName", "")).trim();
        if (sender.isEmpty() || ApplicationNotificationDefaults.DEFAULT_FROM_NAME.equals(sender)) {
            email.put("fromName", app != null ? displayNames.of(app) : ApplicationDisplayName.PLATFORM_NAME);
        }
        String tenantName = app == null
                ? ApplicationNotificationDefaults.DEFAULT_FROM_NAME
                : tenants.findById(app.getTenantId()).map(Tenant::getName).orElse(ApplicationNotificationDefaults.DEFAULT_FROM_NAME);
        // Apps without their own SMTP server send through the platform one (EmailDeliveryService).
        boolean smtpConfigured = smtpSettings.isConfigured(applicationId) || platformSmtp.isConfigured();
        return new ApplicationEmailContext(applicationId, notifications, email, smtpConfigured, tenantName);
    }

    private Map<String, Object> mergedMap(UUID applicationId, String key) {
        Map<String, Object> defaults =
                "email".equals(key)
                        ? ApplicationNotificationDefaults.emailDefaults()
                        : ApplicationNotificationDefaults.notificationDefaults();
        Map<String, Object> merged = new HashMap<>(defaults);
        appSettings
                .findByApplicationIdAndKey(applicationId, key)
                .map(ApplicationSetting::getValue)
                .filter(Map.class::isInstance)
                .ifPresent(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> override = (Map<String, Object>) value;
                    merged.putAll(override);
                });
        return merged;
    }

    public record ApplicationEmailContext(
            UUID applicationId,
            Map<String, Object> notifications,
            Map<String, Object> email,
            boolean smtpConfigured,
            String tenantName) {}
}
