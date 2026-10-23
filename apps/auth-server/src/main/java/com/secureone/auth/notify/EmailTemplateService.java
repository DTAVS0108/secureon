package com.secureone.auth.notify;

import com.secureone.auth.application.ApplicationSetting;
import com.secureone.auth.application.ApplicationSettingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmailTemplateService {

    public static final String SETTINGS_KEY = "email_templates";

    /**
     * Emails that carry a sign-in secret (a reset, sign-in or verification link, or a code). They go to the user
     * only: cc and bcc are never added, whatever a template says.
     */
    public static final Set<String> CARRIES_SECRET = Set.of(
            "password_reset", "password_reset_otp", "magic_link", "login_otp_email", "verify_email",
            "verify_email_otp", "set_password_invite", "signup_account_exists", "signup_phone_in_use",
            "account_locked");

    /**
     * Security notices the user must get when their sign-in changes (NIST SP 800-63B 6.1.2.3): they cannot be
     * switched off. Their wording can still be changed.
     */
    public static final Set<String> ALWAYS_SENT = Set.of("password_changed", "account_security_notice", "account_locked");

    /** Display-only markers added to templates for the console; never stored. */
    private static final Set<String> MARKERS = Set.of("carriesSecret", "alwaysSent");

    private final ApplicationSettingRepository appSettings;
    private final ApplicationEmailContextFactory emailContext;

    public EmailTemplateService(
            ApplicationSettingRepository appSettings, ApplicationEmailContextFactory emailContext) {
        this.appSettings = appSettings;
        this.emailContext = emailContext;
    }

    public Map<String, Object> getPlatformDefaults() {
        return new LinkedHashMap<>(EmailTemplateDefaults.platformDefaults());
    }

    public Map<String, Object> getTemplates(UUID applicationId) {
        Map<String, Object> stored = loadMap(applicationId);
        return withMarkers(stored.isEmpty() ? getPlatformDefaults() : mergeWithDefaults(stored));
    }

    /** Tells the console which templates cannot have cc/bcc or be switched off. */
    private static Map<String, Object> withMarkers(Map<String, Object> templates) {
        Map<String, Object> out = new LinkedHashMap<>();
        templates.forEach((key, value) -> {
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
                copy.put("carriesSecret", CARRIES_SECRET.contains(key));
                copy.put("alwaysSent", ALWAYS_SENT.contains(key));
                if (CARRIES_SECRET.contains(key)) {
                    copy.put("cc", List.of());
                    copy.put("bcc", List.of());
                }
                if (ALWAYS_SENT.contains(key)) {
                    copy.put("enabled", true);
                }
                out.put(key, copy);
            } else {
                out.put(key, value);
            }
        });
        return out;
    }

    public Map<String, Object> saveTemplates(UUID applicationId, Map<String, Object> body) {
        Map<String, Object> defaults = EmailTemplateDefaults.platformDefaults();
        Map<String, Object> stored = loadMap(applicationId);
        Map<String, Object> incoming = body != null ? body : Map.of();
        Set<String> keys = new HashSet<>(stored.keySet());
        keys.addAll(incoming.keySet());

        for (String key : keys) {
            Object incomingValue = incoming.get(key);
            if (!(incomingValue instanceof Map<?, ?> incomingMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedIncoming = new LinkedHashMap<>((Map<String, Object>) incomingMap);
            typedIncoming.keySet().removeAll(MARKERS);
            if (CARRIES_SECRET.contains(key)) {
                typedIncoming.put("cc", List.of());
                typedIncoming.put("bcc", List.of());
            }
            if (ALWAYS_SENT.contains(key)) {
                typedIncoming.put("enabled", true);
            }
            Map<String, Object> override = diffFromDefault(key, typedIncoming, defaults);
            if (override.isEmpty()) {
                stored.remove(key);
            } else {
                stored.put(key, override);
            }
        }

        if (stored.isEmpty()) {
            deleteStored(applicationId);
        } else {
            persist(applicationId, stored);
        }
        return getTemplates(applicationId);
    }

    public Map<String, Object> resetTemplate(UUID applicationId, String templateKey) {
        Map<String, Object> stored = loadMap(applicationId);
        if (stored.remove(templateKey) != null) {
            if (stored.isEmpty()) {
                deleteStored(applicationId);
            } else {
                persist(applicationId, stored);
            }
        }
        return getTemplates(applicationId);
    }

    public Map<String, Object> getTemplate(UUID applicationId, String key) {
        Object template = getTemplates(applicationId).get(key);
        if (!(template instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Unknown email template: " + key);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    public RenderedEmail render(UUID applicationId, String templateKey, Map<String, String> variables) {
        Map<String, Object> template = getTemplate(applicationId, templateKey);
        if (Boolean.FALSE.equals(template.get("enabled")) && !ALWAYS_SENT.contains(templateKey)) {
            throw new IllegalStateException("Email template is disabled: " + templateKey);
        }
        String subject = EmailTemplateRenderer.render(string(template, "subject", ""), variables);
        String bodyText = EmailTemplateRenderer.render(string(template, "bodyText", ""), variables);
        String bodyHtml = EmailTemplateRenderer.renderHtml(string(template, "bodyHtml", ""), variables);
        if (CARRIES_SECRET.contains(templateKey) || variables.containsKey("otpCode")) {
            return new RenderedEmail(subject, bodyText, bodyHtml, List.of(), List.of());
        }
        var notifications = emailContext.context(applicationId).notifications();
        List<String> cc = EmailRecipientResolver.resolve(
                notifications, SmtpSettingsService.parseEmailList(template.get("cc")));
        List<String> bcc = EmailRecipientResolver.resolve(
                notifications, SmtpSettingsService.parseEmailList(template.get("bcc")));
        return new RenderedEmail(subject, bodyText, bodyHtml, cc, bcc);
    }

    public RenderedEmail renderPlatform(String templateKey, Map<String, String> variables) {
        Map<String, Object> template = getPlatformTemplate(templateKey);
        if (Boolean.FALSE.equals(template.get("enabled"))) {
            throw new IllegalStateException("Email template is disabled: " + templateKey);
        }
        String subject = EmailTemplateRenderer.render(string(template, "subject", ""), variables);
        String bodyText = EmailTemplateRenderer.render(string(template, "bodyText", ""), variables);
        String bodyHtml = EmailTemplateRenderer.renderHtml(string(template, "bodyHtml", ""), variables);
        return new RenderedEmail(subject, bodyText, bodyHtml, List.of(), List.of());
    }

    private Map<String, Object> getPlatformTemplate(String key) {
        Object template = EmailTemplateDefaults.platformDefaults().get(key);
        if (!(template instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Unknown email template: " + key);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private Map<String, Object> loadMap(UUID applicationId) {
        return appSettings
                .findByApplicationIdAndKey(applicationId, SETTINGS_KEY)
                .map(ApplicationSetting::getValue)
                .filter(Map.class::isInstance)
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) value;
                    return new HashMap<>(map);
                })
                .orElseGet(HashMap::new);
    }

    private void persist(UUID applicationId, Map<String, Object> value) {
        ApplicationSetting row = appSettings
                .findByApplicationIdAndKey(applicationId, SETTINGS_KEY)
                .orElseGet(() -> {
                    ApplicationSetting created = new ApplicationSetting();
                    created.setApplicationId(applicationId);
                    created.setKey(SETTINGS_KEY);
                    return created;
                });
        row.setValue(new LinkedHashMap<>(value));
        appSettings.save(row);
    }

    private void deleteStored(UUID applicationId) {
        appSettings.findByApplicationIdAndKey(applicationId, SETTINGS_KEY).ifPresent(appSettings::delete);
    }

    private Map<String, Object> mergeWithDefaults(Map<String, Object> stored) {
        Map<String, Object> defaults = EmailTemplateDefaults.platformDefaults();
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : stored.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> custom)) {
                continue;
            }
            Map<String, Object> base = new HashMap<>();
            Object defaultTemplate = defaults.get(entry.getKey());
            if (defaultTemplate instanceof Map<?, ?> defaultMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedDefault = (Map<String, Object>) defaultMap;
                base.putAll(typedDefault);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedCustom = (Map<String, Object>) custom;
            base.putAll(typedCustom);
            merged.put(entry.getKey(), base);
        }
        return merged;
    }

    private static String string(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private static Map<String, Object> diffFromDefault(
            String key, Map<String, Object> template, Map<String, Object> defaults) {
        Object defaultTemplate = defaults.get(key);
        Map<String, Object> base = new LinkedHashMap<>();
        if (defaultTemplate instanceof Map<?, ?> defaultMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedDefault = (Map<String, Object>) defaultMap;
            base.putAll(typedDefault);
        }

        Map<String, Object> override = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            if (!Objects.equals(normalizeValue(entry.getValue()), normalizeValue(base.get(entry.getKey())))) {
                override.put(entry.getKey(), entry.getValue());
            }
        }
        return override;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        return value;
    }

    public record RenderedEmail(String subject, String bodyText, String bodyHtml, List<String> cc, List<String> bcc) {

        public RenderedEmail {
            cc = cc != null ? List.copyOf(cc) : List.of();
            bcc = bcc != null ? List.copyOf(bcc) : List.of();
        }

        public RenderedEmail withExtraRecipients(List<String> extraCc, List<String> extraBcc) {
            return new RenderedEmail(
                    subject,
                    bodyText,
                    bodyHtml,
                    mergeDistinct(cc, extraCc),
                    mergeDistinct(bcc, extraBcc));
        }

        private static List<String> mergeDistinct(List<String> base, List<String> extra) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(base != null ? base : List.of());
            if (extra != null) {
                merged.addAll(extra);
            }
            return List.copyOf(merged);
        }
    }
}
