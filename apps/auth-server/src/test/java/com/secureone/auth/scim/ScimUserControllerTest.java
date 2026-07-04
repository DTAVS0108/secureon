package com.secureone.auth.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.webhook.UserLifecycleWebhookPublisher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ScimUserControllerTest {

    @Mock
    private UserAccountRepository users;

    @Mock
    private UserLifecycleWebhookPublisher userLifecycleWebhooks;

    private ScimUserController controller;

    private UUID userId;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        controller = new ScimUserController(users, userLifecycleWebhooks);
        userId = UUID.randomUUID();
        user = new UserAccount();
        user.setId(userId);
        user.setTenantId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setUsername("user@example.com");
        user.setDisplayName("Jane Doe");
        user.setStatus("ACTIVE");
    }

    @Test
    void deleteSoftDisablesUser() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Void> response = controller.delete(userId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(captor.capture());
        assertEquals("DISABLED", captor.getValue().getStatus());
    }

    @Test
    void patchReplaceActiveDisablesUser() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                "Operations",
                        List.of(Map.of("op", "replace", "path", "active", "value", false)));

        Map<String, Object> out = controller.patch(userId, body);

        assertFalse(Boolean.TRUE.equals(out.get("active")));
        verify(users).save(user);
        assertEquals("DISABLED", user.getStatus());
    }

    @Test
    void putUpdatesDisplayName() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> out = controller.replace(userId, Map.of("displayName", "Updated Name", "active", true));

        assertEquals("Updated Name", out.get("displayName"));
        assertTrue(Boolean.TRUE.equals(out.get("active")));
    }

    @Test
    void deleteNotFoundThrows() {
        when(users.findById(userId)).thenReturn(Optional.empty());
        try {
            controller.delete(userId);
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }
}
