package com.secureone.auth.logging;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the client IP behind reverse proxies (X-Forwarded-For, X-Real-IP). */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return trimToNull(request.getRemoteAddr());
    }

    public static String fromMdcOrUnknown() {
        String ip = org.slf4j.MDC.get(RequestContext.CLIENT_IP);
        return ip != null && !ip.isBlank() ? ip : "unknown";
    }

    static String firstForwardedIp(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String part : header.split(",")) {
            String trimmed = trimToNull(part);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
