package com.secureone.auth.federation;

import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.authn.OAuthLoginRedirectSupport;
import com.secureone.auth.authn.SignedInAuthentication;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final MfaLoginService mfaLogin;
    private final SamlLoginService saml;
    private final SamlLogoutService samlLogout;

    public FederationLoginService(
            IdentityProviderService providers,
            FederationStateStore states,
            UserIdentityRepository identities,
            UserAccountRepository users,
            TenantRepository tenants,
            TenantUserDetailsService userDetailsService,
            ApplicationAccessService access,
            MfaLoginService mfaLogin,
            SamlLoginService saml,
            SamlLogoutService samlLogout) {
        this.providers = providers;
        this.states = states;
        this.identities = identities;
        this.users = users;
        this.tenants = tenants;
        this.userDetailsService = userDetailsService;
        this.access = access;
        this.mfaLogin = mfaLogin;
        this.saml = saml;
        this.samlLogout = samlLogout;
    }

    /**
     * Sends the browser to the external IdP: an OAuth/OIDC authorization redirect, or a SAML AuthnRequest.
     * A continue URL is kept only when it is an OAuth endpoint on this server (no open redirect).
     */
    public ResponseEntity<?> startLogin(
            UUID tenantId,
            UUID applicationId,
            String alias,
            String continueUrl,
            HttpServletRequest request,
            HttpServletResponse response) {
        IdentityProvider provider = providers.requireEnabledProvider(tenantId, applicationId, alias);
        String safeContinue = OAuthLoginRedirectSupport.isSafeContinue(request, continueUrl) ? continueUrl.trim() : null;
        String state = states.issue(new FederationStateStore.PendingState(
                provider.getId(), tenantId, applicationId, safeContinue));
        if (FederationProviderType.SAML.name().equalsIgnoreCase(provider.getType())) {
            return saml.start(provider, state, request, response);
        }
        return ResponseEntity.status(302)
                .header("Location", providers.buildAuthorizationRedirect(provider, state))
                .build();
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
        return completeLogin(provider, pending, profile, request, response);
    }

    /** The IdP posted a SAML response for this provider; RelayState carries our federation state. */
    public ResponseEntity<?> handleSamlResponse(
            UUID providerId, String relayState, HttpServletRequest request, HttpServletResponse response) {
        FederationStateStore.PendingState pending = states.peek(relayState)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired federation state."));
        if (!providerId.equals(pending.providerId())) {
            throw new IllegalArgumentException("Federation state mismatch.");
        }
        IdentityProvider provider = providers.requireEnabledSamlProvider(providerId, pending.applicationId());
        // Validates signature, audience, recipient, validity window and InResponseTo before anything else.
        SamlLoginService.SamlLogin login = saml.acceptResponse(provider, request, response);
        states.consume(relayState);
        ResponseEntity<?> result = completeLogin(provider, pending, login.profile(), request, response);
        samlLogout.rememberSession(request, providerId, login);
        return result;
    }

    /**
     * IdP-initiated sign-in (the user opened the app from the IdP's portal): a response with no request of
     * ours. Only for providers the operator opted in ({@code allowIdpInitiated}); the response must not
     * answer an AuthnRequest (no InResponseTo), is accepted once (replay cache), and the user lands on the
     * operator-configured {@code landingUrl} (or the signed-in page).
     */
    public ResponseEntity<?> handleUnsolicitedSamlResponse(
            UUID providerId, HttpServletRequest request, HttpServletResponse response) {
        IdentityProvider provider = providers.requireEnabledSamlProvider(providerId, null);
        if (!bool(provider.getConfig().get("allowIdpInitiated"))) {
            throw new IllegalArgumentException("IdP-initiated sign-in is not allowed for this provider; start sign-in from the application.");
        }
        SamlLoginService.SamlLogin login = saml.acceptResponse(provider, request, response);
        Object landing = provider.getConfig().get("landingUrl");
        FederationStateStore.PendingState pending = new FederationStateStore.PendingState(
                provider.getId(),
                provider.getTenantId(),
                provider.getApplicationId(),
                landing == null || String.valueOf(landing).isBlank() ? null : String.valueOf(landing).trim());
        ResponseEntity<?> result = completeLogin(provider, pending, login.profile(), request, response);
        samlLogout.rememberSession(request, providerId, login);
        return result;
    }

    private ResponseEntity<?> completeLogin(
            IdentityProvider provider,
            FederationStateStore.PendingState pending,
            FederationUserProfile profile,
            HttpServletRequest request,
            HttpServletResponse response) {
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
                SignedInAuthentication.of(details, FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY);
        // The external sign-in is the first step: the app's MFA requirement and the user's second factor apply
        // as for a password (platform policy when no app). The browser goes to the second step when needed.
        String next = mfaLogin.completeBrowserSignIn(
                authentication, pending.applicationId(), pending.continueUrl(), request, response);
        if (next != null) {
            return ResponseEntity.status(302).header("Location", next).build();
        }
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


    private static boolean bool(Object raw) {
        return raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw));
    }
}
