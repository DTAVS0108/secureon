package com.secureone.auth.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ScimGroupServiceTest {

    @Mock
    private ScimGroupRepository groups;

    @Mock
    private ScimGroupMemberRepository members;

    @Mock
    private UserAccountRepository users;

    private ScimGroupService service;

    @BeforeEach
    void setUp() {
        service = new ScimGroupService(groups, members, users);
    }

    @Test
    void createPersistsGroupAndMembers() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("member@example.com");

        when(groups.save(any(ScimGroup.class))).thenAnswer(invocation -> {
            ScimGroup group = invocation.getArgument(0);
            group.setId(UUID.randomUUID());
            return group;
        });
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(members.existsByGroupIdAndUserId(any(), any())).thenReturn(false);

        Map<String, Object> created = service.create(
                tenantId,
                Map.of(
                        "displayName",
                        "Engineering",
                        "members",
                        List.of(Map.of("value", userId.toString()))));

        assertEquals("Engineering", created.get("displayName"));
        verify(groups).save(any(ScimGroup.class));
        verify(members).save(any(ScimGroupMember.class));
    }

    @Test
    void deleteRemovesMembersFirst() {
        UUID groupId = UUID.randomUUID();
        ScimGroup group = new ScimGroup();
        group.setId(groupId);
        group.setTenantId(UUID.randomUUID());
        group.setDisplayName("Ops");
        when(groups.findById(groupId)).thenReturn(Optional.of(group));

        service.delete(groupId);

        verify(members).deleteByGroupId(groupId);
        verify(groups).delete(group);
    }

    @Test
    void createRequiresDisplayName() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.create(UUID.randomUUID(), Map.of("tenantId", UUID.randomUUID().toString())));
    }
}
