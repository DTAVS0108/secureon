package com.secureone.auth.admin.role;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.rbac.ApplicationRbacScope;
import com.secureone.auth.rbac.RbacGroup;
import com.secureone.auth.rbac.RbacGroupRbacRepository;
import com.secureone.auth.rbac.RbacGroupRepository;
import com.secureone.auth.rbac.Role;
import com.secureone.auth.rbac.RoleRepository;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupAdminService {

    private final RbacGroupRepository groupRepository;
    private final RbacGroupRbacRepository groupRbac;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;

    public GroupAdminService(
            RbacGroupRepository groupRepository,
            RbacGroupRbacRepository groupRbac,
            RoleRepository roleRepository,
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            ApplicationRepository applicationRepository,
            AuditService auditService) {
        this.groupRepository = groupRepository;
        this.groupRbac = groupRbac;
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GroupAdminDtos.GroupSummaryResponse> listGroups(UUID applicationId) {
        requireApplication(applicationId);
        return groupRepository.findByApplicationIdOrderByNameAsc(applicationId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupAdminDtos.GroupDetailResponse getGroup(UUID groupId, UUID applicationId) {
        RbacGroup group = requireGroup(groupId, applicationId);
        return toDetail(group);
    }

    public GroupAdminDtos.GroupDetailResponse create(GroupAdminDtos.GroupCreateRequest request) {
        requireTenant(request.tenantId());
        requireApplication(request.applicationId());
        String name = request.name().trim();
        if (groupRepository.existsByApplicationIdAndName(request.applicationId(), name)) {
            throw new ConflictException("Group name already exists for this application: " + name);
        }
        RbacGroup group = new RbacGroup();
        group.setTenantId(request.tenantId());
        group.setApplicationId(request.applicationId());
        group.setName(name);
        group.setDescription(request.description());
        try {
            groupRepository.save(group);
            groupRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Group name already exists for this application: " + name);
        }
        syncAssignments(group, request.roleIds(), request.memberUserIds());
        auditService.record(
                request.tenantId(), "admin", "group.created", "rbac_group", group.getId(), group.getName(), true);
        return toDetail(group);
    }

    public GroupAdminDtos.GroupDetailResponse update(
            UUID groupId, UUID applicationId, GroupAdminDtos.GroupUpdateRequest request) {
        RbacGroup group = requireGroup(groupId, applicationId);
        String name = request.name().trim();
        if (!group.getName().equals(name)
                && groupRepository.existsByApplicationIdAndName(group.getApplicationId(), name)) {
            throw new ConflictException("Group name already exists for this application: " + name);
        }
        group.setName(name);
        group.setDescription(request.description());
        try {
            groupRepository.save(group);
            groupRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Group name already exists for this application: " + name);
        }
        syncAssignments(group, request.roleIds(), request.memberUserIds());
        auditService.record(
                group.getTenantId(), "admin", "group.updated", "rbac_group", group.getId(), group.getName(), true);
        return toDetail(group);
    }

    public void delete(UUID groupId, UUID applicationId) {
        RbacGroup group = requireGroup(groupId, applicationId);
        groupRepository.delete(group);
        auditService.record(
                group.getTenantId(), "admin", "group.deleted", "rbac_group", groupId, group.getName(), true);
    }

    private void syncAssignments(RbacGroup group, List<UUID> roleIds, List<UUID> memberUserIds) {
        List<UUID> roles = roleIds != null ? roleIds : List.of();
        List<UUID> members = memberUserIds != null ? memberUserIds : List.of();
        validateRoles(group.getApplicationId(), roles);
        validateMembers(group.getTenantId(), members);
        groupRbac.replaceRoles(group.getId(), roles);
        groupRbac.replaceMembers(group.getId(), members);
    }

    private void validateRoles(UUID applicationId, List<UUID> roleIds) {
        for (UUID roleId : roleIds) {
            Role role = roleRepository
                    .findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
            if (!applicationId.equals(role.getApplicationId())) {
                throw new IllegalArgumentException("Role does not belong to this application.");
            }
            if (!ApplicationRbacScope.isApplicationScopedRole(role)) {
                throw new IllegalArgumentException("Console operator roles cannot be assigned to groups.");
            }
        }
    }

    private void validateMembers(UUID tenantId, List<UUID> userIds) {
        for (UUID userId : userIds) {
            UserAccount user = userAccountRepository
                    .findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            if (!tenantId.equals(user.getTenantId())) {
                throw new IllegalArgumentException("User does not belong to this tenant.");
            }
        }
    }

    private GroupAdminDtos.GroupSummaryResponse toSummary(RbacGroup group) {
        return new GroupAdminDtos.GroupSummaryResponse(
                group.getId(),
                group.getTenantId(),
                group.getApplicationId(),
                group.getName(),
                group.getDescription(),
                group.getCreatedAt(),
                groupRbac.countRoles(group.getId()),
                groupRbac.countMembers(group.getId()));
    }

    private GroupAdminDtos.GroupDetailResponse toDetail(RbacGroup group) {
        List<UUID> roleIds = groupRbac.findRoleIdsByGroupId(group.getId());
        List<UUID> memberIds = groupRbac.findMemberUserIdsByGroupId(group.getId());
        List<RoleAdminDtos.RoleRefResponse> roles = groupRbac.findRolesByGroupId(group.getId()).stream()
                .map(r -> new RoleAdminDtos.RoleRefResponse(r.id(), r.name()))
                .toList();
        Map<UUID, Instant> addedAtByUser = new LinkedHashMap<>();
        for (RbacGroupRbacRepository.GroupMemberRef member : groupRbac.findMembersByGroupId(group.getId())) {
            addedAtByUser.put(member.userId(), member.addedAt());
        }
        List<GroupAdminDtos.GroupMemberResponse> members = memberIds.stream()
                .map(userAccountRepository::findById)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(UserAccount::getLoginKey, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new GroupAdminDtos.GroupMemberResponse(
                        u.getId(),
                        u.getLoginKey(),
                        u.getUsername(),
                        u.getDisplayName(),
                        u.getStatus(),
                        u.isEmailVerified(),
                        addedAtByUser.get(u.getId())))
                .toList();
        return new GroupAdminDtos.GroupDetailResponse(
                group.getId(),
                group.getTenantId(),
                group.getApplicationId(),
                group.getName(),
                group.getDescription(),
                group.getCreatedAt(),
                roleIds.size(),
                members.size(),
                roleIds,
                memberIds,
                roles,
                members);
    }

    private RbacGroup requireGroup(UUID groupId, UUID applicationId) {
        return groupRepository
                .findByIdAndApplicationId(groupId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found");
        }
    }

    private void requireApplication(UUID applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found");
        }
    }
}
