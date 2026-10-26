package com.secureone.auth.admin.application;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationCreateRequest;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationResponse;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationUpdateRequest;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSchemaMigrator;
import com.secureone.auth.application.ApplicationSchemaProvisioner;
import com.secureone.auth.application.ApplicationSchemaService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.rbac.RbacBootstrapService;
import com.secureone.auth.tenant.TenantQuotaService;
import com.secureone.auth.tenant.TenantRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationAdminService {

    private final ApplicationRepository applicationRepository;
    private final OAuthClientRepository oauthClients;
    private final TenantRepository tenantRepository;
    private final ApplicationSchemaProvisioner schemaProvisioner;
    private final ApplicationSchemaMigrator schemaMigrator;
    private final ApplicationSchemaService schemaService;
    private final AuditService auditService;
    private final EmailNotificationService emailService;
    private final RbacBootstrapService rbacBootstrap;
    private final TenantQuotaService tenantQuotas;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.platform.SettingsExposureService> exposure;

    public ApplicationAdminService(
            ApplicationRepository applicationRepository,
            OAuthClientRepository oauthClients,
            TenantRepository tenantRepository,
            ApplicationSchemaProvisioner schemaProvisioner,
            ApplicationSchemaMigrator schemaMigrator,
            ApplicationSchemaService schemaService,
            AuditService auditService,
            EmailNotificationService emailService,
            RbacBootstrapService rbacBootstrap,
            TenantQuotaService tenantQuotas) {
        this.applicationRepository = applicationRepository;
        this.oauthClients = oauthClients;
        this.tenantRepository = tenantRepository;
        this.schemaProvisioner = schemaProvisioner;
        this.schemaMigrator = schemaMigrator;
        this.schemaService = schemaService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.rbacBootstrap = rbacBootstrap;
        this.tenantQuotas = tenantQuotas;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list() {
        return applicationRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listByTenant(UUID tenantId) {
        requireTenant(tenantId);
        return applicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id) {
        return toResponse(require(id));
    }

    public ApplicationResponse create(ApplicationCreateRequest request) {
        requireTenant(request.tenantId());
        tenantQuotas.assertCanCreateApplication(request.tenantId());
        String slug = slugify(request.slug() != null ? request.slug() : request.name());
        if (applicationRepository.findByTenantIdAndSlug(request.tenantId(), slug).isPresent()) {
            throw new ConflictException("Application slug already exists for tenant: " + slug);
        }
        Application app = new Application();
        app.setTenantId(request.tenantId());
        app.setName(request.name().trim());
        app.setDescription(trimOrNull(request.description()));
        app.setSlug(slug);
        app.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        applicationRepository.save(app);
        applicationRepository.flush();
        String schemaName = schemaProvisioner.provision(app.getId(), slug);
        app.setSchemaName(schemaName);
        applicationRepository.save(app);
        schemaService.applySearchPathForApplication(app);
        rbacBootstrap.seedDefaultRoles(request.tenantId(), app.getId());
        schemaService.applyPlatformSearchPath();
        auditService.record(
                request.tenantId(), "admin", "application.created", "application", app.getId(), app.getName(), true);
        emailService.sendAdminNotification(app.getId(), "Application registered", "New application: " + app.getName());
        return toResponse(app);
    }

    public ApplicationResponse update(UUID id, ApplicationUpdateRequest request) {
        Application app = require(id);
        app.setName(request.name().trim());
        app.setDescription(trimOrNull(request.description()));
        app.setStatus(normalizeStatus(request.status(), app.getStatus()));
        auditService.record(
                app.getTenantId(), "admin", "application.updated", "application", app.getId(), app.getName(), true);
        return toResponse(app);
    }

    public ApplicationResponse isolate(UUID id) {
        Application app = require(id);
        if (app.getSchemaName() != null && !app.getSchemaName().isBlank()) {
            throw new ConflictException("Application already isolated in schema: " + app.getSchemaName());
        }
        String schemaName = schemaProvisioner.provision(app.getId(), app.getSlug());
        schemaMigrator.migrateLegacyDataToSchema(app.getId(), schemaName);
        app.setSchemaName(schemaName);
        applicationRepository.save(app);
        schemaService.applyPlatformSearchPath();
        auditService.record(
                app.getTenantId(),
                "admin",
                "application.isolated",
                "application",
                app.getId(),
                app.getName() + " → " + schemaName,
                true);
        emailService.sendAdminNotification(
                app.getId(),
                "Application isolated",
                "Moved IAM data to schema " + schemaName + " for application: " + app.getName());
        return toResponse(app);
    }

    public void delete(UUID id) {
        Application app = require(id);
        String schemaName = app.getSchemaName();
        applicationRepository.delete(app);
        applicationRepository.flush();
        schemaProvisioner.drop(schemaName);
        // Its settings-exposure exceptions go with it (they would otherwise linger in the console list).
        if (exposure != null) {
            exposure.ifAvailable(e -> {
                if (e.getOverrides().containsKey(id.toString())) {
                    e.saveOverrides(id, java.util.Map.of());
                }
            });
        }
        auditService.record(
                app.getTenantId(), "admin", "application.deleted", "application", id, app.getName(), true);
        emailService.sendAdminNotification(id, "Application deleted", "Removed application: " + app.getName());
    }

    private Application require(UUID id) {
        return applicationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private ApplicationResponse toResponse(Application app) {
        return new ApplicationResponse(
                app.getId(),
                app.getTenantId(),
                app.getName(),
                app.getSlug(),
                app.getDescription(),
                app.getStatus().toLowerCase(Locale.ROOT),
                app.getSchemaName(),
                oauthClients.countByApplicationId(app.getId()),
                app.getCreatedAt(),
                app.getUpdatedAt());
    }

    private static String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) return defaultStatus;
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> "ACTIVE";
            case "disabled" -> "DISABLED";
            case "suspended" -> "SUSPENDED";
            default -> status.trim().toUpperCase(Locale.ROOT);
        };
    }

    private static String slugify(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
