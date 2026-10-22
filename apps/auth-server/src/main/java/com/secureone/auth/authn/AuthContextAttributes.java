package com.secureone.auth.authn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Session attributes for authentication method references (amr) and ACR level. */
public final class AuthContextAttributes {

    public static final String SESSION_AMR = "secureone.amr";
    public static final String SESSION_ACR = "secureone.acr";
    public static final String SESSION_IMPERSONATED_BY = "secureone.impersonatedBy";
    /** Epoch millis of the last sign-in (OIDC max_age). */
    public static final String SESSION_AUTH_TIME = "secureone.authTime";

    /** Assurance of the current session: "0" none, "1" one step, "2" two steps. */
    public static int acrLevel(HttpServletRequest request) {
        try {
            return Integer.parseInt(acr(request));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** When the session signed in, or null. */
    public static java.time.Instant authTime(HttpServletRequest request) {
        HttpSession session = request != null ? request.getSession(false) : null;
        Object raw = session != null ? session.getAttribute(SESSION_AUTH_TIME) : null;
        return raw instanceof Number n ? java.time.Instant.ofEpochMilli(n.longValue()) : null;
    }

    private AuthContextAttributes() {}

    public static void recordPasswordAuth(HttpServletRequest request) {
        mergeAmr(request, "pwd");
        setAcr(request, "1");
    }

    /**
     * Records how this sign-in was completed, from the factor authorities on the session authentication, and
     * replaces whatever an earlier sign-in in the same browser session left. amr values follow RFC 8176
     * ({@code pwd}, {@code otp}, {@code hwk}, {@code user}, {@code mfa}); {@code fed} marks an external identity
     * provider and {@code passkey} is kept for apps that read it. acr: 1 = one factor, 2 = two steps or a passkey.
     */
    public static void recordSignIn(HttpServletRequest request, org.springframework.security.core.Authentication authentication) {
        HttpSession session = session(request);
        if (session == null) {
            return;
        }
        Set<String> amr = new LinkedHashSet<>();
        boolean twoSteps = false;
        if (authentication != null) {
            for (var authority : authentication.getAuthorities()) {
                String name = authority.getAuthority();
                if (FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(name)) {
                    amr.add("pwd");
                } else if (FactorGrantedAuthority.OTT_AUTHORITY.equals(name)) {
                    amr.add("otp");
                } else if (FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY.equals(name)
                        || FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY.equals(name)) {
                    amr.add("fed");
                } else if (FactorGrantedAuthority.WEBAUTHN_AUTHORITY.equals(name)) {
                    // A passkey with user verification: something you have plus a PIN or biometric.
                    amr.add("hwk");
                    amr.add("user");
                    amr.add("passkey");
                    twoSteps = true;
                } else if (SignedInAuthentication.MFA_FACTOR.equals(name)) {
                    amr.add("otp");
                    twoSteps = true;
                }
            }
        }
        if (amr.isEmpty()) {
            amr.add("pwd");
        }
        if (twoSteps) {
            amr.add("mfa");
        }
        session.setAttribute(SESSION_AMR, List.copyOf(amr));
        session.setAttribute(SESSION_ACR, twoSteps ? "2" : "1");
        long now = System.currentTimeMillis();
        session.setAttribute(SESSION_AUTH_TIME, now);
        if (twoSteps) {
            // A sign-in with two steps counts as a fresh "verify it's you" for sensitive account changes.
            session.setAttribute(com.secureone.auth.twostep.FreshStep.SESSION_KEY, now);
        } else {
            session.removeAttribute(com.secureone.auth.twostep.FreshStep.SESSION_KEY);
        }
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
