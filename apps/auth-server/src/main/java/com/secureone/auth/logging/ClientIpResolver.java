package com.secureone.auth.logging;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the client IP behind reverse proxies (X-Forwarded-For, X-Real-IP). */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    /**
     * The client address as the servlet container reports it. Forwarded headers are applied by the
     * container (server.forward-headers-strategy=native: Tomcat's RemoteIpValve honours
     * X-Forwarded-For only from trusted internal proxies), so a client cannot choose its own address
     * here by sending the header itself (which would also give it a fresh rate-limit bucket per request).
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return trimToNull(request.getRemoteAddr());
    }

    public static String fromMdcOrUnknown() {
        String ip = org.slf4j.MDC.get(RequestContext.CLIENT_IP);
        return ip != null && !ip.isBlank() ? ip : "unknown";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
