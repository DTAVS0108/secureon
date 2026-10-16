package com.secureone.auth.logging;

import com.secureone.auth.tenant.TenantRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;

/** Populates MDC for auth/login log lines so async DB appenders retain session and tenant context. */
public final class AuthRequestContext {

    private AuthRequestContext() {}

    public static void enrichForLogin(HttpServletRequest request, String username, TenantRepository tenants) {
        enrichForLogin(request, username, tenants, null);
    }

    /** {@code resolvedApplicationId}: the app the sign-in is for, when known beyond the form field. */
    public static void enrichForLogin(
            HttpServletRequest request, String username, TenantRepository tenants, UUID resolvedApplicationId) {
        String sessionId = resolveSessionId(request);
        if (sessionId != null) {
            MDC.put(RequestContext.SESSION_ID, sessionId);
        }
        String ip = request.getRemoteAddr();
        if (ip != null) {
            MDC.put(RequestContext.CLIENT_IP, ip);
        }
        String applicationId = request.getParameter("applicationId");
        if (applicationId == null || applicationId.isBlank()) {
            applicationId = request.getParameter("application_id");
        }
        if (resolvedApplicationId != null) {
            MDC.put(RequestContext.APPLICATION_ID, resolvedApplicationId.toString());
        } else if (applicationId != null && !applicationId.isBlank()) {
            MDC.put(RequestContext.APPLICATION_ID, applicationId.trim());
        }
        if (username != null && username.contains(":")) {
            int sep = username.indexOf(':');
            String slug = username.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            MDC.put(RequestContext.TENANT_SLUG, slug);
            MDC.put(RequestContext.PRINCIPAL, username.trim().toLowerCase(Locale.ROOT));
            tenants.findBySlug(slug).ifPresent(tenant -> MDC.put("tenantId", tenant.getId().toString()));
        }
    }

    public static String resolveSessionId(HttpServletRequest request) {
        String fromMdc = MDC.get(RequestContext.SESSION_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JSESSIONID".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return request.getSession(false) != null ? request.getSession(false).getId() : null;
    }

    public static String formatSessionRef(HttpServletRequest request) {
        String sessionId = resolveSessionId(request);
        return sessionId != null ? sessionId : "—";
    }
}
