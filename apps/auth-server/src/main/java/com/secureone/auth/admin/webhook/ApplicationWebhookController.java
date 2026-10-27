package com.secureone.auth.admin.webhook;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.webhook.WebhookEndpointService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * One application's own webhook endpoints, for its administrators (Settings: reading needs {@code app:read},
 * changes {@code settings:write}). They receive that application's events only; the tenant's endpoints, managed on
 * the tenant, receive every application's.
 */
@Tag(name = "Admin — application webhooks", description = "Webhook endpoints that receive one application's events")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/applications/{applicationId}/settings/webhooks")
public class ApplicationWebhookController {

    private final WebhookEndpointService service;
    private final ApplicationRepository applications;

    public ApplicationWebhookController(WebhookEndpointService service, ApplicationRepository applications) {
        this.service = service;
        this.applications = applications;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID applicationId) {
        return service.listForApplication(tenantOf(applicationId), applicationId);
    }

    @GetMapping("/events")
    public List<Map<String, String>> events(@PathVariable UUID applicationId) {
        return com.secureone.auth.webhook.WebhookEvents.CATALOG.entrySet().stream()
                .map(e -> Map.of("event", e.getKey(), "description", e.getValue()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantOf(applicationId);
        Map<String, Object> scoped = new LinkedHashMap<>(body);
        scoped.put("applicationId", applicationId.toString());
        Map<String, Object> out = service.create(tenantId, scoped);
        audited(tenantId, applicationId, "webhook.created", out);
        return out;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable UUID applicationId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID tenantId = requireOwn(applicationId, id);
        Map<String, Object> scoped = new LinkedHashMap<>(body);
        scoped.remove("applicationId"); // stays this application's
        Map<String, Object> out = service.update(tenantId, id, scoped);
        audited(tenantId, applicationId, "webhook.updated", out);
        return out;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID applicationId, @PathVariable UUID id) {
        UUID tenantId = requireOwn(applicationId, id);
        Map<String, Object> before = service.get(tenantId, id);
        service.delete(tenantId, id);
        audited(tenantId, applicationId, "webhook.deleted", before);
    }

    @GetMapping("/{id}/deliveries")
    public List<Map<String, Object>> deliveries(@PathVariable UUID applicationId, @PathVariable UUID id) {
        return service.listDeliveries(requireOwn(applicationId, id), id);
    }

    @PostMapping("/{id}/rotate-secret")
    public Map<String, Object> rotateSecret(
            @PathVariable UUID applicationId, @PathVariable UUID id, @RequestParam(defaultValue = "24") int keepOldHours) {
        UUID tenantId = requireOwn(applicationId, id);
        Map<String, Object> out = service.rotateSecret(tenantId, id, keepOldHours);
        audited(tenantId, applicationId, "webhook.secret_rotated", out);
        return out;
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable UUID applicationId, @PathVariable UUID id) {
        return service.sendTest(requireOwn(applicationId, id), id);
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/redeliver")
    public Map<String, Object> redeliver(
            @PathVariable UUID applicationId, @PathVariable UUID id, @PathVariable UUID deliveryId) {
        return service.redeliver(requireOwn(applicationId, id), id, deliveryId);
    }

    private UUID tenantOf(UUID applicationId) {
        return applications.findById(applicationId)
                .map(a -> a.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    /** The application's tenant, once the endpoint is known to be this application's. */
    private UUID requireOwn(UUID applicationId, UUID id) {
        UUID tenantId = tenantOf(applicationId);
        service.requireApplicationEndpoint(tenantId, applicationId, id);
        return tenantId;
    }

    private void audited(UUID tenantId, UUID applicationId, String action, Map<String, Object> webhook) {
        if (audit != null) {
            audit.ifAvailable(a -> a.record(tenantId, applicationId, "admin", action, "webhook",
                    UUID.fromString(String.valueOf(webhook.get("id"))), String.valueOf(webhook.get("url")), true));
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> audit;
}
