package com.secureone.auth.scim;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "SCIM 2.0 provisioning", description = "Users and groups provisioned by an identity provider (SCIM bearer token)")
@RestController
@RequestMapping("/scim/v2/Groups")
public class ScimGroupController {

    private final ScimGroupService groups;

    public ScimGroupController(ScimGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request, @RequestParam(required = false) UUID tenantId) {
        List<Map<String, Object>> resources = groups.list(ScimScope.resolve(request, tenantId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        out.put("totalResults", resources.size());
        out.put("itemsPerPage", resources.size());
        out.put("startIndex", 1);
        out.put("Resources", resources);
        return out;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable UUID id) {
        ScimScope.requireVisible(request, groups.tenantOf(id));
        return groups.get(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        UUID tenantId = ScimScope.resolve(request, parseTenantId(body));
        Map<String, Object> created = groups.create(tenantId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public Map<String, Object> replace(HttpServletRequest request, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ScimScope.requireVisible(request, groups.tenantOf(id));
        return groups.replace(id, body);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> patch(HttpServletRequest request, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ScimScope.requireVisible(request, groups.tenantOf(id));
        return groups.patch(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        ScimScope.requireVisible(request, groups.tenantOf(id));
        groups.delete(id);
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private static UUID parseTenantId(Map<String, Object> body) {
        Object ext = body.get("urn:secureone:params:scim:schemas:extension:tenant:2.0:Group");
        if (ext instanceof Map<?, ?> map && map.get("tenantId") != null) {
            return UUID.fromString(String.valueOf(map.get("tenantId")));
        }
        Object tenantId = body.get("tenantId");
        // Optional: a tenant SCIM token already determines the tenant (see ScimScope).
        return tenantId == null ? null : UUID.fromString(String.valueOf(tenantId));
    }
}
