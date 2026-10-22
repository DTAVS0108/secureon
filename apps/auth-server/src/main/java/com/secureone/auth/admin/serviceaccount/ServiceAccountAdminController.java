package com.secureone.auth.admin.serviceaccount;

import com.secureone.auth.serviceaccount.ServiceAccountService;
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

@Tag(name = "Admin — service accounts", description = "Machine-to-machine service accounts and API keys")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/service-accounts")
public class ServiceAccountAdminController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    private final ServiceAccountService service;

    public ServiceAccountAdminController(ServiceAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID tenantId) {
        return service.list(tenantId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return service.create(tenantId, body);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return service.update(tenantId, id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
    }

    @GetMapping("/{id}/keys")
    public List<Map<String, Object>> listKeys(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.listKeys(tenantId, id);
    }

    @PostMapping("/{id}/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createKey(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            @PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        stepUp.require(stepUpRequest); // a recent second step from the admin
        return service.createApiKey(tenantId, id, body != null ? body : Map.of());
    }

    @DeleteMapping("/{id}/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(@PathVariable UUID tenantId, @PathVariable UUID id, @PathVariable UUID keyId) {
        service.revokeKey(tenantId, id, keyId);
    }
}
