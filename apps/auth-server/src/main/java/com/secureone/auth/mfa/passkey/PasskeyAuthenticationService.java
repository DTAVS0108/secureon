package com.secureone.auth.mfa.passkey;

import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationTenantResolver;
import com.secureone.auth.authn.LoginSuccessHandler;
import com.secureone.auth.authn.SignedInAuthentication;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.UserVerificationRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasskeyAuthenticationService {

    private final RelyingParty relyingParty;
    private final WebAuthnCredentialRepository credentials;
    private final WebAuthnChallengeStore challenges;
    private final AuthSettingsService authSettings;
    private final org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings;
    private final TenantRepository tenants;
    private final ApplicationTenantResolver applicationTenants;
    private final TenantUserDetailsService userDetailsService;
    private final ApplicationAccessService access;
    private final ApplicationRepository applications;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSuccessHandler loginSuccessHandler;
    private final MfaFactorRepository mfaFactors;

    public PasskeyAuthenticationService(
            RelyingParty relyingParty,
            WebAuthnCredentialRepository credentials,
            WebAuthnChallengeStore challenges,
            AuthSettingsService authSettings,
            TenantRepository tenants,
            ApplicationTenantResolver applicationTenants,
            TenantUserDetailsService userDetailsService,
            ApplicationAccessService access,
            ApplicationRepository applications,
            SecurityContextRepository securityContextRepository,
            LoginSuccessHandler loginSuccessHandler,
            MfaFactorRepository mfaFactors,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings) {
        this.relyingParty = relyingParty;
        this.credentials = credentials;
        this.challenges = challenges;
        this.authSettings = authSettings;
        this.effectiveSettings = effectiveSettings;
        this.tenants = tenants;
        this.applicationTenants = applicationTenants;
        this.userDetailsService = userDetailsService;
        this.access = access;
        this.applications = applications;
        this.securityContextRepository = securityContextRepository;
        this.loginSuccessHandler = loginSuccessHandler;
        this.mfaFactors = mfaFactors;
    }

    public Map<String, Object> beginAuthentication(String tenantSlug, String email, UUID applicationId) {
        assertPasskeysAvailable();
        String resolvedTenantSlug = resolveTenantSlug(tenantSlug, applicationId);
        // Required, not "preferred": a passkey counts as two steps only when the device checked a PIN or biometric.
        StartAssertionOptions.StartAssertionOptionsBuilder builder =
                StartAssertionOptions.builder().userVerification(UserVerificationRequirement.REQUIRED);
        String username = null;
        if (email != null && !email.isBlank() && resolvedTenantSlug != null) {
            username = PasskeyRegistrationService.username(resolvedTenantSlug, email);
            builder.username(username);
        }
        AssertionRequest request = WebAuthnErrors.uncheck(() -> relyingParty.startAssertion(builder.build()));
        String requestId = challenges.saveAuthentication(new WebAuthnChallengeStore.AuthenticationPending(
                username, WebAuthnErrors.uncheck(request::toJson)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", requestId);
        out.put("publicKey", WebAuthnErrors.uncheck(request::toCredentialsGetJson));
        return out;
    }

    /**
     * Own transaction per step (like the email-code sign-in): the application is only known from the body, so
     * the membership lookup must start after it is set, in that application's schema and tenant scope.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> finishAuthentication(
            UUID requestId,
            String credentialJson,
            UUID applicationId,
            HttpServletRequest request,
            HttpServletResponse response) {
        assertPasskeysAvailable();
        WebAuthnChallengeStore.AuthenticationPending pending = challenges
                .consumeAuthentication(requestId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Passkey sign-in expired or invalid."));
        AssertionRequest assertionRequest =
                WebAuthnErrors.uncheck(() -> AssertionRequest.fromJson(pending.requestJson()));
        AssertionResult result = WebAuthnErrors.uncheck(() -> relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                        .request(assertionRequest)
                        .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                        .build()));
        if (!result.isSuccess()) {
            throw new IllegalArgumentException("Passkey verification failed.");
        }
        String username = result.getUsername();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Passkey sign-in did not resolve a user.");
        }
        UserAccount user = credentials
                .resolveUser(username)
                .orElseThrow(() -> new IllegalArgumentException("Passkey user not found."));
        updateSignatureCount(result);
        // Same rule as password and external sign-in: the user must have access to the app (or the app must allow
        // self registration). Signing in with a passkey used to add the user to any app named in the request.
        if (applicationId != null) {
            // Not signed in yet, so no tenant scope: read the app's tenant in platform scope.
            boolean sameTenant = Boolean.TRUE.equals(RlsScope.asPlatform(() -> applications.findById(applicationId)
                    .map(app -> app.getTenantId().equals(user.getTenantId()))
                    .orElse(false)));
            if (!sameTenant || !access.admit(applicationId, user.getId())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "This account has not been granted access to this application.");
            }
        }
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        UserDetails details = userDetailsService.buildSessionPrincipal(tenant.getSlug(), user);
        Authentication authentication = SignedInAuthentication.of(details, FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.noContent().build();
    }

    /**
     * Second step with a passkey or security key: assertion options limited to this user's credentials, with
     * user verification required. Not gated on passkey sign-in being enabled: the two-step policy decides.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Map<String, Object> beginSecondStep(String tenantSlug, String email) {
        String username = PasskeyRegistrationService.username(tenantSlug, email);
        AssertionRequest request = WebAuthnErrors.uncheck(() -> relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(username)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build()));
        String requestId = challenges.saveAuthentication(new WebAuthnChallengeStore.AuthenticationPending(
                username, WebAuthnErrors.uncheck(request::toJson)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", requestId);
        out.put("publicKey", WebAuthnErrors.uncheck(request::toCredentialsGetJson));
        return out;
    }

    /** Verifies a second-step assertion; true only when it is valid and belongs to {@code expectedUserId}. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public boolean finishSecondStep(UUID requestId, String credentialJson, UUID expectedUserId) {
        WebAuthnChallengeStore.AuthenticationPending pending = challenges
                .consumeAuthentication(requestId.toString())
                .orElse(null);
        if (pending == null) {
            return false;
        }
        try {
            AssertionRequest assertionRequest =
                    WebAuthnErrors.uncheck(() -> AssertionRequest.fromJson(pending.requestJson()));
            AssertionResult result = WebAuthnErrors.uncheck(() -> relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(assertionRequest)
                            .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                            .build()));
            if (!result.isSuccess() || result.getUsername() == null) {
                return false;
            }
            boolean sameUser = credentials.resolveUser(result.getUsername())
                    .map(u -> u.getId().equals(expectedUserId))
                    .orElse(false);
            if (sameUser) {
                updateSignatureCount(result);
            }
            return sameUser;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void updateSignatureCount(AssertionResult result) {
        String credentialId = WebAuthnCredentialRepository.encode(result.getCredentialId());
        mfaFactors.findPasskeyByCredentialId(credentialId).ifPresent(factor -> credentials.updateSignatureCount(
                factor, result.getSignatureCount()));
    }

    private void assertPasskeysAvailable() {
        if (!effectiveSettings.getObject().isMethodAvailableForCurrentApplication("m_passkey")) {
            throw new IllegalStateException("Passkeys are not enabled for this application.");
        }
    }

    private String resolveTenantSlug(String tenantSlug, UUID applicationId) {
        if (applicationId != null) {
            return applicationTenants.requireTenantSlug(applicationId);
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return null;
        }
        return tenantSlug.trim().toLowerCase(Locale.ROOT);
    }
}
