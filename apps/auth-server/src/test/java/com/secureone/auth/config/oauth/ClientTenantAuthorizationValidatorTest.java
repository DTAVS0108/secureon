package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@ExtendWith(MockitoExtension.class)
class ClientTenantAuthorizationValidatorTest {

    private static final String CLIENT_ID = "acme-web";
    private static final String REDIRECT_URI = "https://app.acme.test/callback";

    @Mock
    private ApplicationOAuthClientResolver applications;
    @Mock
    private TenantRepository tenants;
    @Mock
    private UserAccountRepository users;
    @Mock
    private ApplicationAccessService access;

    private ClientTenantAuthorizationValidator validator;
    private Application app;
    private Tenant tenant;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        validator = new ClientTenantAuthorizationValidator(applications, tenants, users, access);

        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setSlug("acme");

        app = new Application();
        app.setId(UUID.randomUUID());
        app.setTenantId(tenant.getId());

        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail("jane@acme.test");
        user.setStatus("ACTIVE");
    }

    @Test
    void anonymousRequestIsLeftForTheLoginPage() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertDoesNotThrow(() -> validator.accept(context(anonymous)));
        verifyNoInteractions(applications, tenants, users, access);
    }

    @Test
    void userFromAnotherTenantIsDenied() {
        stubClientTenant();

        OAuth2AuthorizationCodeRequestAuthenticationException ex = assertThrows(
                OAuth2AuthorizationCodeRequestAuthenticationException.class,
                () -> validator.accept(context(signedIn("globex:jane@acme.test"))));

        assertEquals(OAuth2ErrorCodes.ACCESS_DENIED, ex.getError().getErrorCode());
        verify(access, never()).admit(any(), any());
    }

    @Test
    void platformOperatorIsDenied() {
        stubClientTenant();

        assertThrows(
                OAuth2AuthorizationCodeRequestAuthenticationException.class,
                () -> validator.accept(context(signedIn("admin"))));
        verify(access, never()).admit(any(), any());
    }

    @Test
    void tenantUserWithoutAccessIsDenied() {
        stubClientTenant();
        when(users.findByTenantIdAndEmail(tenant.getId(), "jane@acme.test")).thenReturn(Optional.of(user));
        when(access.admit(app.getId(), user.getId())).thenReturn(false);

        OAuth2AuthorizationCodeRequestAuthenticationException ex = assertThrows(
                OAuth2AuthorizationCodeRequestAuthenticationException.class,
                () -> validator.accept(context(signedIn("acme:jane@acme.test"))));

        assertEquals(OAuth2ErrorCodes.ACCESS_DENIED, ex.getError().getErrorCode());
    }

    @Test
    void admittedTenantUserPasses() {
        stubClientTenant();
        when(users.findByTenantIdAndEmail(tenant.getId(), "jane@acme.test")).thenReturn(Optional.of(user));
        when(access.admit(app.getId(), user.getId())).thenReturn(true);

        assertDoesNotThrow(() -> validator.accept(context(signedIn("ACME:Jane@acme.test"))));
    }

    @Test
    void suspendedUserWithABrowserSessionGetsNoCode() {
        stubClientTenant();
        user.setStatus("SUSPENDED");
        when(users.findByTenantIdAndEmail(tenant.getId(), "jane@acme.test")).thenReturn(Optional.of(user));

        OAuth2AuthorizationCodeRequestAuthenticationException ex = assertThrows(
                OAuth2AuthorizationCodeRequestAuthenticationException.class,
                () -> validator.accept(context(signedIn("acme:jane@acme.test"))));

        assertEquals(OAuth2ErrorCodes.ACCESS_DENIED, ex.getError().getErrorCode());
        verify(access, never()).admit(any(), any());
    }

    @Test
    void userOfASuspendedTenantGetsNoCode() {
        stubClientTenant();
        tenant.setStatus("SUSPENDED");
        when(users.findByTenantIdAndEmail(tenant.getId(), "jane@acme.test")).thenReturn(Optional.of(user));

        assertThrows(
                OAuth2AuthorizationCodeRequestAuthenticationException.class,
                () -> validator.accept(context(signedIn("acme:jane@acme.test"))));
        verify(access, never()).admit(any(), any());
    }

    @Test
    void unregisteredRedirectUriIsStillRejectedByDefaults() {
        OAuth2AuthorizationCodeRequestAuthenticationToken token = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                "https://auth.test/oauth2/authorize",
                CLIENT_ID,
                signedIn("acme:jane@acme.test"),
                "https://evil.test/callback",
                "state",
                Set.of("openid"),
                Map.of());

        assertThrows(
                OAuth2AuthorizationCodeRequestAuthenticationException.class,
                () -> validator.accept(OAuth2AuthorizationCodeRequestAuthenticationContext.with(token)
                        .registeredClient(client())
                        .build()));
        verifyNoInteractions(applications, access);
    }

    private void stubClientTenant() {
        when(applications.findActiveByOAuthClientId(CLIENT_ID)).thenReturn(Optional.of(app));
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
    }

    private static Authentication signedIn(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(
                name, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationContext context(Authentication principal) {
        OAuth2AuthorizationCodeRequestAuthenticationToken token = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                "https://auth.test/oauth2/authorize",
                CLIENT_ID,
                principal,
                REDIRECT_URI,
                "state",
                Set.of("openid"),
                Map.of());
        return OAuth2AuthorizationCodeRequestAuthenticationContext.with(token)
                .registeredClient(client())
                .build();
    }

    private static RegisteredClient client() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("openid")
                .build();
    }
}
