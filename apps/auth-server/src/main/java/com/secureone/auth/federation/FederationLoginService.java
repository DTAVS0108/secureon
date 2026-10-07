package com.secureone.auth.federation;

import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.authn.LoginSuccessHandler;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.federation.IdentityProviderService.FederationUserProfile;
import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FederationLoginService {

    private final IdentityProviderService providers;
    private final FederationStateStore states;
    private final UserIdentityRepository identities;
    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final TenantUserDetailsService userDetailsService;
    private final ApplicationAccessService access;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSuccessHandler loginSuccessHandler;
    private final MfaLoginService mfaLogin;

    public FederationLoginService(
            IdentityProviderService providers,
            FederationStateStore states,
            UserIdentityRepository identities,
            UserAccountRepository users,
            TenantRepository tenants,
            TenantUserDetailsService userDetailsService,
            ApplicationAccessService access,
            SecurityContextRepository securityContextRepository,
            LoginSuccessHandler loginSuccessHandler,
            MfaLoginService mfaLogin) {
        this.providers = providers;
        this.states = states;
        this.identities = identities;
        this.users = users;
        this.tenants = tenants;
        this.userDetailsService = userDetailsService;
        this.access = access;
        this.securityContextRepository = securityContextRepository;
        this.loginSuccessHandler = loginSuccessHandler;
        this.mfaLogin = mfaLogin;
    }

    public String startLogin(
            UUID tenantId, UUID applicationId, String alias, String continueUrl, HttpServletResponse response) {
        IdentityProvider provider = providers.requireEnabledProvider(tenantId, applicationId, alias);
        String state = states.issue(new FederationStateStore.PendingState(
                provider.getId(), tenantId, applicationId, continueUrl));
        return providers.buildAuthorizationRedirect(provider, state);
    }

    public ResponseEntity<?> handleCallback(
            String alias,
            String code,
            String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        FederationStateStore.PendingState pending = states.consume(state)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired federation state."));
        IdentityProvider provider = providers.requireEnabledProvider(
                pending.tenantId(), pending.applicationId(), alias);
        if (!provider.getId().equals(pending.providerId())) {
            throw new IllegalArgumentException("Federation state mismatch.");
        }
        FederationUserProfile profile = providers.exchangeCode(provider, code);
        if (profile.email() == null || profile.email().isBlank()) {
            throw new IllegalArgumentException("Identity provider did not return a verified email address.");
        }
        UserAccount user = resolveOrCreateUser(provider, pending, profile);
        linkIdentity(provider, user, profile);
        if (pending.applicationId() != null && !access.admit(pending.applicationId(), user.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "This account has not been granted access to this application.");
        }
        Tenant tenant = tenants.findById(pending.tenantId()).orElseThrow();
        UserDetails details = userDetailsService.buildSessionPrincipal(tenant.getSlug(), user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        if (pending.applicationId() != null && mfaLogin.shouldChallengeFormLogin(authentication, pending.applicationId())) {
            return mfaLogin.completePasswordLogin(authentication, pending.applicationId(), request, response);
        }
        establishSession(authentication, request, response);
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        if (pending.continueUrl() != null && !pending.continueUrl().isBlank()) {
            return ResponseEntity.status(302).header("Location", pending.continueUrl()).build();
        }
        return ResponseEntity.status(302).header("Location", "/login.html?signedIn=1").build();
    }

    private UserAccount resolveOrCreateUser(
            IdentityProvider provider, FederationStateStore.PendingState pending, FederationUserProfile profile) {
        return identities
                .findByProviderIdAndProviderSubject(provider.getId(), profile.subject())
                .flatMap(link -> users.findById(link.getUserId()))
                .orElseGet(() -> resolveByEmail(provider, pending, profile));
    }

    private UserAccount resolveByEmail(
            IdentityProvider provider, FederationStateStore.PendingState pending, FederationUserProfile profile) {
        String email = profile.email().trim().toLowerCase(Locale.ROOT);
        UserAccount existing = users.findByTenantIdAndEmail(pending.tenantId(), email).orElse(null);
        if (existing != null) {
            if (!bool(provider.getConfig().get("linkByEmail"))) {
                throw new IllegalStateException("An account with this email already exists. Sign in with password first.");
            }
            if (!profile.emailVerified()) {
                // Linking on an unverified address would let anyone who can claim the email take over the account.
                throw new IllegalStateException("The identity provider did not confirm this email address, so it cannot be linked.");
            }
            return existing;
        }
        if (!bool(provider.getConfig().get("jitProvisioning"))) {
            throw new IllegalStateException("No matching account. Just-in-time provisioning is disabled.");
        }
        UserAccount created = new UserAccount();
        created.setTenantId(pending.tenantId());
        created.setEmail(email);
        created.setEmailVerified(profile.emailVerified());
        created.setUsername(email);
        created.setDisplayName(email);
        created.setStatus("ACTIVE");
        created.setType("USER");
        return users.save(created);
    }

    private void linkIdentity(IdentityProvider provider, UserAccount user, FederationUserProfile profile) {
        UserIdentity link = identities
                .findByProviderIdAndProviderSubject(provider.getId(), profile.subject())
                .orElseGet(UserIdentity::new);
        link.setUserId(user.getId());
        link.setProviderId(provider.getId());
        link.setProviderSubject(profile.subject());
        link.setEmail(profile.email().trim().toLowerCase(Locale.ROOT));
        Map<String, Object> meta = new HashMap<>();
        meta.put("providerType", provider.getType());
        link.setMetadata(meta);
        identities.save(link);
    }

    private void establishSession(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static boolean bool(Object raw) {
        return raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw));
    }
}
