package com.secureone.auth.scim;

import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ScimGroupService {

    private final ScimGroupRepository groups;
    private final ScimGroupMemberRepository members;
    private final UserAccountRepository users;

    public ScimGroupService(
            ScimGroupRepository groups, ScimGroupMemberRepository members, UserAccountRepository users) {
        this.groups = groups;
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return groups.findByTenantIdOrderByDisplayNameAsc(tenantId).stream()
                .map(this::toScimGroup)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        ScimGroup group = requireGroup(id);
        return toScimGroup(group);
    }

    public Map<String, Object> create(UUID tenantId, Map<String, Object> body) {
        String displayName = string(body.get("displayName"), null);
        if (displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required.");
        }
        String externalId = string(body.get("externalId"), null);
        if (externalId != null && !externalId.isBlank()) {
            groups.findByTenantIdAndExternalId(tenantId, externalId.trim())
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "externalId already in use.");
                    });
        }
        ScimGroup group = new ScimGroup();
        group.setTenantId(tenantId);
        group.setDisplayName(displayName.trim());
        if (externalId != null && !externalId.isBlank()) {
            group.setExternalId(externalId.trim());
        }
        group = groups.save(group);
        replaceMembers(group, extractMemberIds(body, tenantId));
        return toScimGroup(group);
    }

    public Map<String, Object> replace(UUID id, Map<String, Object> body) {
        ScimGroup group = requireGroup(id);
        applyGroupFields(group, body);
        group = groups.save(group);
        if (body.containsKey("members")) {
            replaceMembers(group, extractMemberIds(body, group.getTenantId()));
        }
        return toScimGroup(group);
    }

    public Map<String, Object> patch(UUID id, Map<String, Object> body) {
        ScimGroup group = requireGroup(id);
        applyPatchOperations(group, body);
        group = groups.save(group);
        return toScimGroup(group);
    }

    public void delete(UUID id) {
        ScimGroup group = requireGroup(id);
        members.deleteByGroupId(group.getId());
        groups.delete(group);
    }

    private ScimGroup requireGroup(UUID id) {
        return groups.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void applyGroupFields(ScimGroup group, Map<String, Object> body) {
        if (body.containsKey("displayName")) {
            String displayName = string(body.get("displayName"), group.getDisplayName());
            if (displayName == null || displayName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName cannot be blank.");
            }
            group.setDisplayName(displayName.trim());
        }
        if (body.containsKey("externalId")) {
            String externalId = string(body.get("externalId"), null);
            if (externalId != null && !externalId.isBlank()) {
                externalId = externalId.trim();
                groups.findByTenantIdAndExternalId(group.getTenantId(), externalId)
                        .filter(existing -> !existing.getId().equals(group.getId()))
                        .ifPresent(existing -> {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "externalId already in use.");
                        });
                group.setExternalId(externalId);
            } else {
                group.setExternalId(null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPatchOperations(ScimGroup group, Map<String, Object> body) {
        Object operations = body.get("Operations");
        if (!(operations instanceof List<?> ops)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operations array is required.");
        }
        for (Object raw : ops) {
            if (!(raw instanceof Map<?, ?> op)) {
                continue;
            }
            String operation = String.valueOf(op.get("op")).toLowerCase(Locale.ROOT);
            String path = op.get("path") != null ? String.valueOf(op.get("path")) : null;
            Object value = op.get("value");
            if ("replace".equals(operation)) {
                if ("displayName".equalsIgnoreCase(path)) {
                    String displayName = string(value, group.getDisplayName());
                    if (displayName == null || displayName.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName cannot be blank.");
                    }
                    group.setDisplayName(displayName.trim());
                } else if ("members".equalsIgnoreCase(path)) {
                    Map<String, Object> wrapper = Map.of("members", value);
                    replaceMembers(group, extractMemberIds(wrapper, group.getTenantId()));
                } else if (path == null && value instanceof Map<?, ?> map) {
                    Map<String, Object> fields = (Map<String, Object>) map;
                    if (fields.containsKey("displayName")) {
                        applyGroupFields(group, fields);
                    }
                    if (fields.containsKey("members")) {
                        replaceMembers(group, extractMemberIds(fields, group.getTenantId()));
                    }
                }
            } else if ("add".equals(operation) && "members".equalsIgnoreCase(path)) {
                addMembers(group, extractMemberIds(Map.of("members", value), group.getTenantId()));
            } else if ("remove".equals(operation) && path != null && path.toLowerCase(Locale.ROOT).startsWith("members")) {
                removeMemberByPath(group, path);
            }
        }
    }

    private void removeMemberByPath(ScimGroup group, String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("value eq")) {
            int start = lower.indexOf('"');
            int end = lower.lastIndexOf('"');
            if (start >= 0 && end > start) {
                UUID userId = UUID.fromString(path.substring(start + 1, end));
                members.deleteById(new ScimGroupMember.ScimGroupMemberId(group.getId(), userId));
            }
        }
    }

    private void replaceMembers(ScimGroup group, List<UUID> memberIds) {
        members.deleteByGroupId(group.getId());
        addMembers(group, memberIds);
    }

    private void addMembers(ScimGroup group, List<UUID> memberIds) {
        for (UUID userId : memberIds) {
            if (members.existsByGroupIdAndUserId(group.getId(), userId)) {
                continue;
            }
            ScimGroupMember row = new ScimGroupMember();
            row.setGroupId(group.getId());
            row.setUserId(userId);
            members.save(row);
        }
    }

    @SuppressWarnings("unchecked")
    private List<UUID> extractMemberIds(Map<String, Object> body, UUID tenantId) {
        Object rawMembers = body.get("members");
        if (!(rawMembers instanceof List<?> list)) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object value = map.get("value");
                if (value == null) {
                    continue;
                }
                UUID userId = UUID.fromString(String.valueOf(value));
                UserAccount user = users.findById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found: " + userId));
                if (!tenantId.equals(user.getTenantId())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "User " + userId + " is not in tenant " + tenantId);
                }
                ids.add(userId);
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<String, Object> toScimGroup(ScimGroup group) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));
        out.put("id", group.getId().toString());
        out.put("displayName", group.getDisplayName());
        if (group.getExternalId() != null) {
            out.put("externalId", group.getExternalId());
        }
        out.put("members", memberViews(group.getId()));
        out.put("meta", Map.of("resourceType", "Group"));
        out.put("tenantId", group.getTenantId().toString());
        return out;
    }

    private List<Map<String, Object>> memberViews(UUID groupId) {
        List<ScimGroupMember> rows = members.findByGroupIdOrderByCreatedAtAsc(groupId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScimGroupMember row : rows) {
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("value", row.getUserId().toString());
            users.findById(row.getUserId()).ifPresent(user -> {
                member.put("display", user.getEmail());
                member.put("$ref", "/scim/v2/Users/" + user.getId());
            });
            out.add(member);
        }
        return out;
    }

    private static String string(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? fallback : value;
    }
}
