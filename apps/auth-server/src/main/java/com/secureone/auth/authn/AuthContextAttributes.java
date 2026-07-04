package com.secureone.auth.authn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Session attributes for authentication method references (amr) and ACR level. */
public final class AuthContextAttributes {

    public static final String SESSION_AMR = "secureone.amr";
    public static final String SESSION_ACR = "secureone.acr";
    public static final String SESSION_IMPERSONATED_BY = "secureone.impersonatedBy";

    private AuthContextAttributes() {}

    public static void recordPasswordAuth(HttpServletRequest request) {
        mergeAmr(request, "pwd");
        setAcr(request, "1");
    }

    public static void recordMfaAuth(HttpServletRequest request, String method) {
        mergeAmr(request, method);
        setAcr(request, "2");
    }

    public static void recordPasskeyAuth(HttpServletRequest request) {
        mergeAmr(request, "passkey");
        setAcr(request, "2");
    }

    public static void markImpersonation(HttpServletRequest request, String adminPrincipal) {
        HttpSession session = session(request);
        if (session != null && adminPrincipal != null) {
            session.setAttribute(SESSION_IMPERSONATED_BY, adminPrincipal);
        }
    }

    public static List<String> amr(HttpServletRequest request) {
        HttpSession session = session(request);
        if (session == null) {
            return List.of();
        }
        Object raw = session.getAttribute(SESSION_AMR);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        return List.of();
    }

    public static String acr(HttpServletRequest request) {
        HttpSession session = session(request);
        if (session == null) {
            return "0";
        }
        Object raw = session.getAttribute(SESSION_ACR);
        return raw != null ? raw.toString() : "0";
    }

    public static List<String> amrFromCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return List.of();
        }
        return amr(attrs.getRequest());
    }

    public static String acrFromCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "0";
        }
        return acr(attrs.getRequest());
    }

    private static void mergeAmr(HttpServletRequest request, String method) {
        HttpSession session = session(request);
        if (session == null || method == null || method.isBlank()) {
            return;
        }
        Set<String> merged = new LinkedHashSet<>(amr(request));
        merged.add(method);
        session.setAttribute(SESSION_AMR, List.copyOf(merged));
    }

    private static void setAcr(HttpServletRequest request, String level) {
        HttpSession session = session(request);
        if (session != null) {
            session.setAttribute(SESSION_ACR, level);
        }
    }

    private static HttpSession session(HttpServletRequest request) {
        return request != null ? request.getSession(true) : null;
    }
}
