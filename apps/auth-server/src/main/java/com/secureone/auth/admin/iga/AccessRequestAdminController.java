package com.secureone.auth.admin.iga;

import com.secureone.auth.iga.AccessRequestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — access requests", description = "Identity governance access request workflow (stub)")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/access-requests")
public class AccessRequestAdminController {

    private final AccessRequestService service;

    public AccessRequestAdminController(AccessRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID tenantId) {
        return service.list(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return service.create(tenantId, body);
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestParam UUID reviewerId) {
        return service.approve(tenantId, id, reviewerId);
    }

    @PostMapping("/{id}/deny")
    public Map<String, Object> deny(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestParam UUID reviewerId) {
        return service.deny(tenantId, id, reviewerId);
    }
}
