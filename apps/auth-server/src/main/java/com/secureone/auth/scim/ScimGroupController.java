package com.secureone.auth.scim;

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

@RestController
@RequestMapping("/scim/v2/Groups")
public class ScimGroupController {

    private final ScimGroupService groups;

    public ScimGroupController(ScimGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) UUID tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId filter is required.");
        }
        List<Map<String, Object>> resources = groups.list(tenantId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        out.put("totalResults", resources.size());
        out.put("itemsPerPage", resources.size());
        out.put("startIndex", 1);
        out.put("Resources", resources);
        return out;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return groups.get(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tenantId = parseTenantId(body);
        Map<String, Object> created = groups.create(tenantId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public Map<String, Object> replace(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return groups.replace(id, body);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> patch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return groups.patch(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
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
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId extension is required.");
        }
        return UUID.fromString(String.valueOf(tenantId));
    }
}
