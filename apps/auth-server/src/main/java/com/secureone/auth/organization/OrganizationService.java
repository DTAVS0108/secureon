package com.secureone.auth.organization;

import com.secureone.auth.admin.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return organizations.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, UUID id) {
        return toResponse(requireOrganization(tenantId, id));
    }

    public Map<String, Object> create(UUID tenantId, Map<String, Object> body) {
        String name = requiredString(body.get("name"), "name");
        String slug = slugify(body.get("slug"), name);
        organizations.findByTenantIdAndSlug(tenantId, slug)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization slug already exists.");
                });
        Organization organization = new Organization();
        organization.setTenantId(tenantId);
        organization.setName(name);
        organization.setSlug(slug);
        organization.setParentId(parseUuid(body.get("parentId")));
        validateParent(tenantId, organization.getParentId(), null);
        return toResponse(organizations.save(organization));
    }

    public Map<String, Object> update(UUID tenantId, UUID id, Map<String, Object> body) {
        Organization organization = requireOrganization(tenantId, id);
        if (body.containsKey("name")) {
            organization.setName(requiredString(body.get("name"), "name"));
        }
        if (body.containsKey("slug")) {
            String slug = slugify(body.get("slug"), organization.getName());
            organizations.findByTenantIdAndSlug(tenantId, slug)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization slug already exists.");
                    });
            organization.setSlug(slug);
        }
        if (body.containsKey("parentId")) {
            UUID parentId = parseUuid(body.get("parentId"));
            validateParent(tenantId, parentId, id);
            organization.setParentId(parentId);
        }
        return toResponse(organizations.save(organization));
    }

    public void delete(UUID tenantId, UUID id) {
        Organization organization = requireOrganization(tenantId, id);
        organizations.delete(organization);
    }

    private Organization requireOrganization(UUID tenantId, UUID id) {
        return organizations.findById(id)
                .filter(row -> tenantId.equals(row.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + id));
    }

    private void validateParent(UUID tenantId, UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization cannot be its own parent.");
        }
        Organization parent = requireOrganization(tenantId, parentId);
        if (selfId == null) {
            return;
        }
        // Walk up from the new parent; reaching this organization means the move would create a cycle.
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Organization cursor = parent; cursor != null && cursor.getParentId() != null; ) {
            if (cursor.getParentId().equals(selfId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Organization cannot be moved under one of its own descendants.");
            }
            if (!seen.add(cursor.getParentId())) {
                break; // existing bad data; stop rather than loop forever
            }
            cursor = organizations.findById(cursor.getParentId()).orElse(null);
        }
    }

    private Map<String, Object> toResponse(Organization organization) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", organization.getId().toString());
        out.put("tenantId", organization.getTenantId().toString());
        out.put("parentId", organization.getParentId() != null ? organization.getParentId().toString() : null);
        out.put("name", organization.getName());
        out.put("slug", organization.getSlug());
        out.put("createdAt", organization.getCreatedAt().toString());
        return out;
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID: " + raw);
        }
    }

    private static String requiredString(Object raw, String field) {
        String value = raw != null ? raw.toString().trim() : "";
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value;
    }

    private static String slugify(Object rawSlug, String fallbackName) {
        String source = rawSlug != null && !rawSlug.toString().isBlank() ? rawSlug.toString() : fallbackName;
        return source.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
