package com.secureone.auth.admin.tenant;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantCreateRequest;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantResponse;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantUpdateRequest;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.tenant.TenantUserRosterRepository;
import com.secureone.auth.tenant.rbac.TenantRbacBootstrapService;
import com.secureone.auth.user.UserAccountRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantAdminService {

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;
    private final EmailNotificationService emailService;
    private final TenantUserRosterRepository tenantUserRoster;
    private final TenantRbacBootstrapService tenantRbacBootstrap;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSchemaProvisioner> schemaProvisioner;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.platform.SettingsExposureService> exposure;

    public TenantAdminService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            ApplicationRepository applicationRepository,
            AuditService auditService,
            EmailNotificationService emailService,
            TenantUserRosterRepository tenantUserRoster,
            TenantRbacBootstrapService tenantRbacBootstrap) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
        this.emailService = emailService;
        this.tenantUserRoster = tenantUserRoster;
        this.tenantRbacBootstrap = tenantRbacBootstrap;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return tenantRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID id) {
        return toResponse(require(id));
    }

    public TenantResponse create(TenantCreateRequest request) {
        String slug = normalizeSlug(request.slug(), request.name());
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new ConflictException("Tenant slug already exists: " + slug);
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.name().trim());
        tenant.setSlug(slug);
        tenant.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        tenant.setSettings(planSettings(request.plan(), "free"));
        tenantRepository.save(tenant);
        tenantRbacBootstrap.seedForTenant(tenant.getId());
        auditService.record(tenant.getId(), "admin", "tenant.created", "tenant", tenant.getId(), tenant.getName(), true);
        emailService.sendAdminNotification(null, "Tenant created", "New tenant: " + tenant.getName());
        return toResponse(tenant);
    }

    public TenantResponse update(UUID id, TenantUpdateRequest request) {
        Tenant tenant = require(id);
        String slug = normalizeSlug(request.slug(), request.name());
        tenantRepository.findBySlug(slug).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("Tenant slug already exists: " + slug);
            }
        });
        if (isSystemTenant(tenant)
                && (!slug.equals(tenant.getSlug())
                        || !"ACTIVE".equalsIgnoreCase(normalizeStatus(request.status(), tenant.getStatus())))) {
            throw new ConflictException("The reserved platform tenant cannot be renamed or suspended.");
        }
        tenant.setName(request.name().trim());
        tenant.setSlug(slug);
        tenant.setStatus(normalizeStatus(request.status(), tenant.getStatus()));
        Map<String, Object> settings = new HashMap<>(tenant.getSettings());
        settings.put("plan", request.plan() != null ? request.plan() : settings.getOrDefault("plan", "free"));
        tenant.setSettings(settings);
        auditService.record(tenant.getId(), "admin", "tenant.updated", "tenant", tenant.getId(), tenant.getName(), true);
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public void assertDeletable(UUID id) {
        if (isSystemTenant(require(id))) {
            throw new ConflictException("The reserved platform tenant cannot be deleted.");
        }
    }

    /** One audit entry for signing out a whole tenant (suspended or deleted). */
    public void recordAccessEnded(UUID id, String name, int users, String why) {
        auditService.record(id, "admin", "tenant.access_ended", "tenant", id,
                name + " " + why + ": " + users + " user(s) signed out", true);
    }

    public void delete(UUID id) {
        Tenant tenant = require(id);
        if (isSystemTenant(tenant)) {
            throw new ConflictException("The reserved platform tenant cannot be deleted.");
        }
        // Each application keeps its users' data in a schema of its own: drop those too (the rows below go with
        // the tenant by cascade, the schemas would not), and its settings exceptions.
        for (var app : applicationRepository.findByTenantIdOrderByCreatedAtDesc(id)) {
            if (schemaProvisioner != null) {
                schemaProvisioner.ifAvailable(p -> p.drop(app.getSchemaName()));
            }
            if (exposure != null) {
                exposure.ifAvailable(e -> {
                    if (e.getOverrides().containsKey(app.getId().toString())) {
                        e.saveOverrides(app.getId(), Map.of());
                    }
                });
            }
        }
        tenantRepository.delete(tenant);
        auditService.record(tenant.getId(), "admin", "tenant.deleted", "tenant", id, tenant.getName(), true);
        emailService.sendAdminNotification(null, "Tenant deleted", "Removed tenant: " + tenant.getName());
    }

    /** The reserved tenant holding platform operator accounts (migration V62). */
    private static boolean isSystemTenant(Tenant tenant) {
        return tenant.getSettings() != null && Boolean.TRUE.equals(tenant.getSettings().get("system"));
    }

    private Tenant require(UUID id) {
        return tenantRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    private TenantResponse toResponse(Tenant tenant) {
        UUID id = tenant.getId();
        return new TenantResponse(
                id,
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus().toLowerCase(Locale.ROOT),
                readPlan(tenant.getSettings()),
                tenantUserRoster.countByTenantId(id),
                applicationRepository.countByTenantId(id),
                tenant.getCreatedAt(),
                isSystemTenant(tenant));
    }

    private static String readPlan(Map<String, Object> settings) {
        if (settings == null) {
            return "free";
        }
        Object plan = settings.get("plan");
        return plan != null ? plan.toString() : "free";
    }

    private static Map<String, Object> planSettings(String plan, String defaultPlan) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("plan", plan != null && !plan.isBlank() ? plan : defaultPlan);
        return settings;
    }

    private static String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) {
            return defaultStatus;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeSlug(String slug, String name) {
        String base = (slug != null && !slug.isBlank()) ? slug : name;
        return base.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
