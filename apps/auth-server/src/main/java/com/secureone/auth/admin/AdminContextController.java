package com.secureone.auth.admin;

import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.platform.FeatureFlagDefaults;
import com.secureone.auth.platform.PlatformFeatures;
import com.secureone.auth.admin.AdminAccessService.ApplicationSummary;
import com.secureone.auth.admin.console.AdminConsoleCapabilityService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — context", description = "Current operator, act-as, accessible tenants and applications")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/context")
public class AdminContextController {

    private final AdminAccessService access;
    private final AdminPermissionService permissions;
    private final AdminConsoleCapabilityService consoleCapabilities;
    private final AdminOperatorResolver operators;
    private final ApplicationRepository applications;
    private final OAuthClientRepository oauthClients;
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final AdminSessionService sessions;
    private final PlatformFeatures platformFeatures;
    private final AuthSettingsService authSettings;
    private final com.secureone.auth.user.UserAvatarService avatars;

    public AdminContextController(
            AdminAccessService access,
            AdminPermissionService permissions,
            AdminConsoleCapabilityService consoleCapabilities,
            AdminOperatorResolver operators,
            ApplicationRepository applications,
            OAuthClientRepository oauthClients,
            TenantRepository tenants,
            UserAccountRepository users,
            AdminSessionService sessions,
            PlatformFeatures platformFeatures,
            AuthSettingsService authSettings,
            com.secureone.auth.user.UserAvatarService avatars) {
        this.access = access;
        this.permissions = permissions;
        this.consoleCapabilities = consoleCapabilities;
        this.operators = operators;
        this.applications = applications;
        this.oauthClients = oauthClients;
        this.tenants = tenants;
        this.users = users;
        this.sessions = sessions;
        this.platformFeatures = platformFeatures;
        this.authSettings = authSettings;
        this.avatars = avatars;
    }

    public record ApplicationContextItem(
            UUID id,
            UUID tenantId,
            String tenantName,
            String tenantSlug,
            String name,
            String slug,
            String status,
            List<String> permissions,
            List<String> consoleFeatures,
            /** May register and manage this application's OAuth clients (Connect an app). */
            boolean canManageClients) {}

    public record AdminContextResponse(
            boolean platformSuperAdmin,
            String operatorTier,
            String principal,
            String actAsEmail,
            String email,
            String displayName,
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            UUID userId,
            long oauthClientCount,
            List<ApplicationContextItem> applications,
            boolean breakGlass,
            java.time.Instant sessionExpiresAt,
            long sessionIdleTimeoutSeconds,
            /** Platform settings → Feature flags → Configuration as code (platform operators only). */
            boolean configAsCodeEnabled,
            /** Platform settings → Feature flags → Connect an app (all operators). */
            boolean connectAppEnabled,
            /** Platform settings → Feature flags → Client setup code (OAuth client pages). */
            boolean clientSetupCodeEnabled,
            /**
             * Console controls (platform-scoped feature flags such as audit_log or api_reference) and whether
             * each is on. Decided in platform settings for every application; sent here so the console never
             * depends on an application's settings being readable.
             */
            Map<String, Boolean> consoleFlags,
            /** When the operator's profile picture last changed (epoch ms; GET /me/avatar?v=…); null without one. */
            Long avatarVersion) {}

    @GetMapping
    public AdminContextResponse context(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            jakarta.servlet.http.HttpServletRequest request) {
        boolean superAdmin = access.canAccessPlatformSettings(authentication, actAsEmail);
        String operatorTier = access.resolveOperatorTier(authentication, actAsEmail);
        String email = access.resolveOperatorEmail(authentication, actAsEmail);
        List<ApplicationSummary> apps = access.accessibleApplications(authentication, actAsEmail);
        String displayName = resolveDisplayName(email, authentication);
        UUID tenantId = null;
        String tenantSlug = null;
        String tenantName = null;
        UUID userId = null;
        if (email != null && operators.resolveTenantSlug(authentication) != null) {
            String slug = operators.resolveTenantSlug(authentication);
            Tenant tenant = tenants.findBySlug(slug).orElse(null);
            if (tenant != null) {
                tenantId = tenant.getId();
                tenantSlug = tenant.getSlug();
                tenantName = tenant.getName();
            }
            final UUID resolvedTenantId = tenantId;
            userId = users.findByEmailIgnoreCase(email).stream()
                    .filter(u -> resolvedTenantId == null || u.getTenantId().equals(resolvedTenantId))
                    .map(UserAccount::getId)
                    .findFirst()
                    .orElse(null);
        }
        final UUID operatorUserId = userId;
        final UUID operatorTenantId = tenantId;
        List<ApplicationContextItem> items = new ArrayList<>();
        for (ApplicationSummary app : apps) {
            Tenant tenant = tenants.findById(app.tenantId()).orElse(null);
            List<String> appPermissions = superAdmin
                    ? List.of()
                    : email != null
                            ? permissions.effectivePermissionKeys(app.id(), email).stream().sorted().toList()
                            : List.of();
            List<String> features = superAdmin
                    ? consoleCapabilities.allFeatureKeys()
                    : operatorUserId != null && operatorTenantId != null
                            ? consoleCapabilities.effectiveFeatureKeys(
                                    operatorUserId, operatorTenantId, app.id())
                            : List.of();
            items.add(new ApplicationContextItem(
                    app.id(),
                    app.tenantId(),
                    tenant != null ? tenant.getName() : app.tenantId().toString(),
                    tenant != null ? tenant.getSlug() : "",
                    app.name(),
                    app.slug(),
                    app.status(),
                    appPermissions,
                    features,
                    access.canManageClients(authentication, actAsEmail, app.id())));
        }
        return new AdminContextResponse(
                superAdmin,
                operatorTier,
                authentication != null ? authentication.getName() : "",
                actAsEmail,
                email,
                displayName,
                tenantId,
                tenantSlug,
                tenantName,
                userId,
                oauthClients.count(),
                items,
                access.isBreakGlass(authentication),
                request.getAttribute(AdminCredentialAuthenticationFilter.SESSION_EXPIRES_AT_ATTR)
                        instanceof java.time.Instant expiresAt ? expiresAt : null,
                sessions.idleTimeoutSeconds(),
                superAdmin && platformFeatures.configAsCodeEnabled(),
                platformFeatures.connectAppEnabled(),
                platformFeatures.clientSetupCodeEnabled(),
                consoleFlags(),
                userId != null ? avatars.version(userId).map(java.time.Instant::toEpochMilli).orElse(null) : null);
    }

    private Map<String, Boolean> consoleFlags() {
        Map<String, Boolean> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> flag : authSettings.getFeatureFlags()) {
            String key = String.valueOf(flag.get("key"));
            if (!FeatureFlagDefaults.isApplicationScoped(key)) {
                out.put(key, Boolean.TRUE.equals(flag.get("enabled")));
            }
        }
        return out;
    }

    private String resolveDisplayName(String email, Authentication authentication) {
        if (email != null) {
            return users.findByEmailIgnoreCase(email).stream()
                    .map(UserAccount::getDisplayName)
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst()
                    .orElse(email);
        }
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "";
    }

    @GetMapping("/tenants")
    public List<Map<String, Object>> tenantsForApplication(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam UUID applicationId,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        access.requireApplicationAccess(authentication, actAsEmail, applicationId);
        var app = applications
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return tenants.findById(app.getTenantId()).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "slug", t.getSlug(),
                        "name", t.getName(),
                        "status", t.getStatus()))
                .toList();
    }
}
