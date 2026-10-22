package com.secureone.auth.admin;

import com.secureone.auth.platform.PlatformFeatures;
import com.secureone.auth.admin.console.AdminConsoleCapabilityService;
import com.secureone.auth.admin.role.RoleApplicationLookup;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

    private static final Pattern APP_PATH =
            Pattern.compile("^/api/admin/v1/applications/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern ROLE_PATH =
            Pattern.compile("^/api/admin/v1/roles/([0-9a-fA-F-]{36})$");
    private static final Pattern PERMISSION_PATH =
            Pattern.compile("^/api/admin/v1/permissions/([0-9a-fA-F-]{36})$");
    private static final Pattern TENANT_PATH =
            Pattern.compile("^/api/admin/v1/tenants/([0-9a-fA-F-]{36})(?:/|$)");
    /** Tenant-scoped resources a tenant super admin may manage for their own tenant. */
    private static final Pattern TENANT_GOVERNANCE_PATH = Pattern.compile(
            "^/api/admin/v1/tenants/[0-9a-fA-F-]{36}/"
                    + "(?:console-access|console-roles|tenant-rbac|organizations|federation/providers|service-accounts|webhooks|access-requests|access-review|change-requests|two-step-policy|scim)"
                    + "(?:/|$)");

    private static final Pattern PLATFORM_WIDE_WITHOUT_APP =
            Pattern.compile("^/api/admin/v1/(?:audit|logs|sessions)(?:/.*)?$");

    private static final Map<String, String> PATH_PERMISSIONS = Map.ofEntries(
            Map.entry("users", "user:read"),
            Map.entry("roles", "role:read"),
            Map.entry("groups", "role:read"),
            Map.entry("permissions", "role:read"),
            Map.entry("audit", "audit:read"),
            Map.entry("logs", "logs:read"),
            Map.entry("sessions", "session:read"),
            Map.entry("settings", "app:read"));

    private final AdminAccessService access;
    private final AdminPermissionService permissions;
    private final AdminConsoleCapabilityService consoleCapabilities;
    private final RoleApplicationLookup rbacLookup;
    private final UserAccountRepository users;
    private final ApplicationRepository applications;
    private final PlatformFeatures platformFeatures;

    public AdminAccessInterceptor(
            AdminAccessService access,
            AdminPermissionService permissions,
            AdminConsoleCapabilityService consoleCapabilities,
            RoleApplicationLookup rbacLookup,
            UserAccountRepository users,
            ApplicationRepository applications,
            PlatformFeatures platformFeatures) {
        this.access = access;
        this.permissions = permissions;
        this.consoleCapabilities = consoleCapabilities;
        this.rbacLookup = rbacLookup;
        this.users = users;
        this.applications = applications;
        this.platformFeatures = platformFeatures;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/v1/") || path.startsWith("/api/admin/v1/auth/")) {
            return true;
        }
        // Only a console session token proves the operator passed sign-in, including MFA.
        if (!Boolean.TRUE.equals(request.getAttribute(AdminCredentialAuthenticationFilter.CONSOLE_SESSION_ATTR))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"detail\":\"Sign in to the admin console again.\"}");
            } catch (java.io.IOException ignored) {
                // Status is already set.
            }
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actAs = request.getHeader("X-Act-As-Email");
        // Acting as another operator is a platform support tool, never a way to borrow a colleague's role.
        if (actAs != null && !actAs.isBlank() && !access.isPlatformSuperAdmin(auth)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "X-Act-As-Email is only available to platform super admins");
        }
        if (path.startsWith("/api/admin/v1/context")
                || path.startsWith("/api/admin/v1/me")
                || path.startsWith("/api/admin/v1/tenant-workspace")) {
            return true;
        }
        if (path.startsWith("/api/admin/v1/config/")) {
            // Configuration files can create tenants and change any of their applications.
            access.requirePlatformSettingsAccess(auth, actAs);
            if (!platformFeatures.configAsCodeEnabled()) {
                // Off unless a platform operator turns it on (Platform settings → Feature flags).
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"detail\":\"Configuration as code is turned off. "
                            + "Turn it on in Platform settings → Feature flags.\"}");
                } catch (java.io.IOException ignored) {
                    // Status is already set.
                }
                return false;
            }
            return true;
        }

        UUID applicationId = parseApplicationId(path, request);
        if (applicationId != null) {
            access.requireApplicationAccess(auth, actAs, applicationId);
            enforcePermission(auth, actAs, applicationId, path, request.getMethod());
        } else if (PLATFORM_WIDE_WITHOUT_APP.matcher(path).matches()) {
            // Without an applicationId these list (or delete) every tenant's records.
            access.requirePlatformSettingsAccess(auth, actAs);
        }

        if (path.startsWith("/api/admin/v1/settings")) {
            access.requirePlatformSettingsAccess(auth, actAs);
            return true;
        }

        if (path.startsWith("/api/admin/v1/tenants")) {
            Matcher tenantMatcher = TENANT_PATH.matcher(path);
            if (tenantMatcher.find() && TENANT_GOVERNANCE_PATH.matcher(path).find()) {
                access.requireTenantGovernanceAccess(auth, actAs, UUID.fromString(tenantMatcher.group(1)));
            } else {
                access.requirePlatformSettingsAccess(auth, actAs);
            }
            return true;
        }

        if (path.equals("/api/admin/v1/applications") && "POST".equalsIgnoreCase(request.getMethod())) {
            access.requirePlatformSettingsAccess(auth, actAs);
        } else if (APP_PATH.matcher(path).find()
                && ("PUT".equalsIgnoreCase(request.getMethod())
                        || "DELETE".equalsIgnoreCase(request.getMethod()))) {
            access.requirePlatformSettingsAccess(auth, actAs);
        }
        return true;
    }

    private void enforcePermission(
            Authentication auth, String actAs, UUID applicationId, String path, String method) {
        if (access.bypassesPermissionChecks(auth, actAs, applicationId)) {
            return;
        }
        String email = access.resolveOperatorEmail(auth, actAs);
        if (email == null) {
            return;
        }
        UUID userId = access.resolveOperatorUserId(auth, actAs).orElse(null);
        if (userId != null && consoleCapabilities.hasConsoleAccess(userId)) {
            // The console role is the grant: the operator may use every section enabled for that
            // role in this app (tenant super admins choose the sections per role).
            enforceConsoleFeature(userId, applicationId, path);
            return;
        }
        String required = requiredPermission(path, method);
        if (required != null) {
            permissions.requirePermission(applicationId, email, required);
        }
    }

    private void enforceConsoleFeature(UUID userId, UUID applicationId, String path) {
        Application app = applications.findById(applicationId).orElse(null);
        if (app == null) {
            return;
        }
        String section = sectionFromPath(path);
        if (section == null) {
            return;
        }
        if (!consoleCapabilities.canAccessFeature(userId, app.getTenantId(), applicationId, section)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Console feature not granted: " + section);
        }
    }

    private static String sectionFromPath(String path) {
        Matcher appSection = Pattern.compile("^/api/admin/v1/applications/[^/]+/([^/]+)").matcher(path);
        if (appSection.find()) {
            return appSection.group(1);
        }
        // App-filtered views of the shared lists, e.g. /api/admin/v1/logs?applicationId=…
        Matcher shared = Pattern.compile("^/api/admin/v1/(audit|logs|sessions)(?:/|$)").matcher(path);
        if (shared.find()) {
            return shared.group(1);
        }
        return null;
    }

    private static String requiredPermission(String path, String method) {
        Matcher appSection = Pattern.compile("^/api/admin/v1/applications/[^/]+/([^/]+)").matcher(path);
        if (appSection.find()) {
            String section = appSection.group(1);
            String readPerm = PATH_PERMISSIONS.get(section);
            if (readPerm == null) {
                return null;
            }
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                return readPerm;
            }
            return writePermission(section, method);
        }
        if (path.matches("^/api/admin/v1/applications/[^/]+$") && "GET".equalsIgnoreCase(method)) {
            return "app:read";
        }
        return null;
    }

    private static String writePermission(String section, String method) {
        return switch (section) {
            case "users" -> "DELETE".equalsIgnoreCase(method) ? "user:delete" : "user:write";
            case "roles", "groups", "permissions" -> "role:write";
            case "settings" -> "settings:write";
            default -> PATH_PERMISSIONS.getOrDefault(section, "app:read");
        };
    }

    private UUID parseApplicationId(String path, HttpServletRequest request) {
        Matcher m = APP_PATH.matcher(path);
        if (m.find()) {
            return UUID.fromString(m.group(1));
        }
        String param = request.getParameter("applicationId");
        if (param != null && !param.isBlank()) {
            return UUID.fromString(param);
        }
        String header = request.getHeader("X-Application-Id");
        if (header != null && !header.isBlank()) {
            return UUID.fromString(header);
        }
        Matcher roleM = ROLE_PATH.matcher(path);
        if (roleM.find()) {
            return rbacLookup
                    .applicationIdForRole(UUID.fromString(roleM.group(1)))
                    .orElse(null);
        }
        Matcher permM = PERMISSION_PATH.matcher(path);
        if (permM.find()) {
            return rbacLookup
                    .applicationIdForPermission(UUID.fromString(permM.group(1)))
                    .orElse(null);
        }
        return null;
    }
}
