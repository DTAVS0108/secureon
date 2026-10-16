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
        return userApplications.findByUserId(user.getId()).stream()
                .map(UserApplication::getApplicationId)
                .filter(smtpSettings::isConfigured)
                .findFirst()
                .orElseGet(() -> userApplications.findByUserId(user.getId()).stream()
                        .map(UserApplication::getApplicationId)
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
