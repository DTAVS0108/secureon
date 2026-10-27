package com.secureone.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Every change to platform settings ({@code /api/admin/v1/settings/...}) and to an application's settings
 * ({@code /api/admin/v1/applications/{id}/settings/...}) goes in the audit log, as {@code settings.<section>_changed}
 * with the values sent (secrets masked). Sections that record a more specific entry themselves are skipped.
 */
public final class SettingsChangeAudit {

    private SettingsChangeAudit() {}

    static final String BODY_ATTR = SettingsChangeAudit.class.getName() + ".body";
    private static final Pattern PLATFORM = Pattern.compile("^/api/admin/v1/settings/([a-z0-9-]+)(/.*)?$");
    private static final Pattern APPLICATION =
            Pattern.compile("^/api/admin/v1/applications/([0-9a-fA-F-]{36})/settings/([a-z0-9-]+)(/.*)?$");
    /** Recorded in more detail where they are saved. */
    private static final Set<String> OWN_AUDIT =
            Set.of("feature-flags", "attack-protection", "log-streams", "signing-keys", "webhooks", "apis", "saml-apps");
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|secret|token|apikey|api_key|credential|privatekey|private_key|pepper).*");
    private static final int MAX_LABEL = 1000;

    /** The section a request changes and its application (null for platform settings), or null when not audited. */
    record Target(String section, UUID applicationId) {}

    static Target target(String method, String path) {
        if (!Set.of("PUT", "POST", "PATCH", "DELETE").contains(method) || path == null) {
            return null;
        }
        Matcher m = APPLICATION.matcher(path);
        String section;
        String rest;
        UUID applicationId = null;
        if (m.matches()) {
            applicationId = UUID.fromString(m.group(1));
            section = m.group(2);
            rest = m.group(3);
        } else {
            m = PLATFORM.matcher(path);
            if (!m.matches()) {
                return null;
            }
            section = m.group(1);
            rest = m.group(2);
        }
        if (OWN_AUDIT.contains(section) || (rest != null && (rest.endsWith("/test") || rest.startsWith("/exceptions")))) {
            return null;
        }
        return new Target(section, applicationId);
    }

    /** "minLength=12, requireSymbol=true, smtp.password=***": what was sent, secrets masked, kept short. */
    static String describe(String section, Object body) {
        List<String> parts = new ArrayList<>();
        flatten("", body, parts);
        String label = section + (parts.isEmpty() ? "" : ": " + String.join(", ", parts));
        return label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL - 1) + "…" : label;
    }

    private static void flatten(String prefix, Object value, List<String> out) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> flatten(prefix.isEmpty() ? String.valueOf(k) : prefix + "." + k, v, out));
        } else if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + "[" + i + "]", list.get(i), out);
            }
        } else if (!prefix.isEmpty()) {
            boolean secret = SECRET.matcher(prefix).matches() && value != null && !String.valueOf(value).isBlank();
            out.add(prefix + "=" + (secret ? "***" : value));
        }
    }

    /** Keeps the request body, so the audit entry can say what was saved. */
    @ControllerAdvice
    public static class BodyCapture extends RequestBodyAdviceAdapter {
        @Override
        public boolean supports(MethodParameter parameter, Type targetType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                attributes.setAttribute(BODY_ATTR, body, RequestAttributes.SCOPE_REQUEST);
            }
            return body;
        }
    }

    /** Writes the entry once the change has been saved (2xx, no exception). */
    @Component
    public static class Recorder implements HandlerInterceptor {

        private final AuditService audit;

        public Recorder(AuditService audit) {
            this.audit = audit;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                Exception ex) {
            if (ex != null || response.getStatus() >= 300) {
                return;
            }
            Target target = target(request.getMethod(), request.getRequestURI());
            if (target == null) {
                return;
            }
            audit.recordIndependently(null, target.applicationId(), "admin",
                    "settings." + target.section().replace('-', '_') + "_changed",
                    target.applicationId() != null ? "application" : "platform_settings", target.applicationId(),
                    describe(target.section(), request.getAttribute(BODY_ATTR)), true);
        }
    }
}
