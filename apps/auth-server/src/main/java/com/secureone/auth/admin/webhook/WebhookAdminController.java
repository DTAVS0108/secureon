package com.secureone.auth.admin.webhook;

import com.secureone.auth.webhook.WebhookEndpointService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — webhooks", description = "Outbound webhook endpoints and delivery history")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/webhooks")
public class WebhookAdminController {

    private final WebhookEndpointService service;

    public WebhookAdminController(WebhookEndpointService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID tenantId) {
        return service.list(tenantId);
    }

    /** Events a webhook can subscribe to, with what each means. */
    @GetMapping("/events")
    public List<Map<String, String>> events(@PathVariable UUID tenantId) {
        return com.secureone.auth.webhook.WebhookEvents.CATALOG.entrySet().stream()
                .map(e -> Map.of("event", e.getKey(), "description", e.getValue()))
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        Map<String, Object> out = service.create(tenantId, body);
        audited(tenantId, "webhook.created", out);
        return out;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> out = service.update(tenantId, id, body);
        audited(tenantId, "webhook.updated", out);
        return out;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        Map<String, Object> before = service.get(tenantId, id);
        service.delete(tenantId, id);
        audited(tenantId, "webhook.deleted", before);
    }

    @GetMapping("/{id}/deliveries")
    public List<Map<String, Object>> listDeliveries(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.listDeliveries(tenantId, id);
    }

    /**
     * New signing secret, shown once. {@code keepOldHours} (default 24, up to 168): the old secret also signs
     * until then, so the receiver can switch without missing events.
     */
    @PostMapping("/{id}/rotate-secret")
    public Map<String, Object> rotateSecret(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int keepOldHours) {
        Map<String, Object> out = service.rotateSecret(tenantId, id, keepOldHours);
        audited(tenantId, "webhook.secret_rotated", out);
        return out;
    }

    /** Sends a test event now; answers with what the endpoint returned. */
    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.sendTest(tenantId, id);
    }

    /** Sends one past delivery again, from the first try. */
    @PostMapping("/{id}/deliveries/{deliveryId}/redeliver")
    public Map<String, Object> redeliver(
            @PathVariable UUID tenantId, @PathVariable UUID id, @PathVariable UUID deliveryId) {
        return service.redeliver(tenantId, id, deliveryId);
    }

    private void audited(UUID tenantId, String action, Map<String, Object> webhook) {
        if (audit != null) {
            audit.ifAvailable(a -> a.record(tenantId, "admin", action, "webhook",
                    UUID.fromString(String.valueOf(webhook.get("id"))), String.valueOf(webhook.get("url")), true));
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> audit;
}
