package com.secureone.auth.authn;

import com.secureone.auth.account.AccountNotificationService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in with an emailed link. The link is the first step only: like any other sign-in it goes through
 * {@link MfaLoginService}, so a user with a second factor (or an app that requires one) is asked for it.
 */
@Service
@Transactional
public class MagicLinkAuthService {

    private final AccountNotificationService accounts;
    private final UserAccountRepository users;
    private final TenantUserDetailsService userDetailsService;
    private final TenantSlugResolver tenantSlugResolver;
    private final ApplicationRepository applications;
    private final MfaLoginService signIn;

    public MagicLinkAuthService(
            AccountNotificationService accounts,
            UserAccountRepository users,
            TenantUserDetailsService userDetailsService,
            TenantSlugResolver tenantSlugResolver,
            ApplicationRepository applications,
            @Lazy MfaLoginService signIn) {
        this.accounts = accounts;
        this.users = users;
        this.userDetailsService = userDetailsService;
        this.tenantSlugResolver = tenantSlugResolver;
        this.applications = applications;
        this.signIn = signIn;
    }

    private record FirstStep(UserAccount user, String loginUsername, Authentication authentication, UUID applicationId) {}

    /** Consumes the link and builds the first-step authentication; the session comes from the sign-in gate. */
    private FirstStep firstStep(String rawToken, UUID requestedApplicationId) {
        Map<String, String> result = accounts.completeMagicLink(rawToken);
        // By id: the same email can exist in several tenants.
        UserAccount user = users.findById(UUID.fromString(result.get("userId"))).orElseThrow();
        String tenantSlug = tenantSlugResolver.slugForTenant(user.getTenantId());
        UserDetails details = userDetailsService.buildSessionPrincipal(tenantSlug, user);
        Authentication authentication = SignedInAuthentication.of(details, FactorGrantedAuthority.OTT_AUTHORITY);
        // The link names the app it was sent for; only an app of the user's own tenant decides MFA.
        UUID applicationId = requestedApplicationId != null
                && applications.findById(requestedApplicationId)
                        .filter(app -> app.getTenantId().equals(user.getTenantId()))
                        .isPresent()
                ? requestedApplicationId
                : null;
        return new FirstStep(user, tenantSlug + ":" + user.getEmail(), authentication, applicationId);
    }

    /** Link opened in the browser: to the second step when needed, otherwise signed in on the sign-in page. */
    public ResponseEntity<Void> completeInBrowser(
            String rawToken, UUID applicationId, HttpServletRequest request, HttpServletResponse response) {
        FirstStep step = firstStep(rawToken, applicationId);
        String next = signIn.completeBrowserSignIn(step.authentication(), step.applicationId(), null, request, response);
        if (next == null) {
            next = "/login.html?signedIn=1"
                    + (step.applicationId() != null ? "&applicationId=" + step.applicationId() : "");
        }
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", next).build();
    }

    /**
     * Link handled by the app's own page: 200 when signed in, otherwise the gate's answer (428 with an MFA
     * challenge, or 403 when the app requires MFA and the user has none).
     */
    public ResponseEntity<?> completeAndCreateSessionApi(
            String rawToken, UUID applicationId, HttpServletRequest request, HttpServletResponse response) {
        FirstStep step = firstStep(rawToken, applicationId);
        ResponseEntity<?> outcome;
        try {
            outcome = signIn.completePasswordLogin(step.authentication(), step.applicationId(), request, response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage()));
        }
        if (!outcome.getStatusCode().is2xxSuccessful()) {
            return outcome;
        }
        return ResponseEntity.ok(Map.of(
                "signedIn", true,
                "email", step.user().getEmail(),
                "loginUsername", step.loginUsername()));
    }
}
