package com.secureone.auth.authn;

import com.secureone.auth.account.PasswordExpiryService;
import com.secureone.auth.mfa.MfaLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    private final AuthenticationManager authenticationManager;
    private final MfaLoginService mfaLogin;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.protection.AttackProtectionService> attackProtection;

    public AuthSessionService(AuthenticationManager authenticationManager, MfaLoginService mfaLogin) {
        this.authenticationManager = authenticationManager;
        this.mfaLogin = mfaLogin;
    }

    public ResponseEntity<?> sessionLogin(
            String tenantSlug, String email, String password, HttpServletRequest request, HttpServletResponse response) {
        return sessionLogin(tenantSlug, email, password, null, request, response);
    }

    public ResponseEntity<?> sessionLogin(
            String tenantSlug,
            String email,
            String password,
            UUID applicationId,
            HttpServletRequest request,
            HttpServletResponse response) {
        String username = tenantSlug.trim().toLowerCase(Locale.ROOT)
                + ":"
                + email.trim().toLowerCase(Locale.ROOT);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            return mfaLogin.completePasswordLogin(authentication, applicationId, request, response);
        } catch (AuthenticationException ex) {
            // Counts towards "CAPTCHA after failures" (Attack protection), as the hosted form does.
            if (attackProtection != null) {
                attackProtection.ifAvailable(p -> p.recordSignInFailure(com.secureone.auth.logging.ClientIpResolver.resolve(request)));
            }
            ProblemDetail problem =
                    ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, loginFailureMessage(ex));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        } catch (IllegalStateException ex) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
        }
    }

    private static String loginFailureMessage(AuthenticationException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        // Unknown, inactive and unverified accounts are rejected before the password is checked;
        // naming the reason would tell anyone which emails have accounts.
        if (cause instanceof UsernameNotFoundException) {
            return GENERIC_FAILURE;
        }
        if (ex instanceof CredentialsExpiredException) {
            return PasswordExpiryService.EXPIRED_LOGIN_MESSAGE;
        }
        if (ex instanceof LockedException) {
            return AccountLockoutService.LOCKED_MESSAGE;
        }
        if (ex instanceof BadCredentialsException) {
            return GENERIC_FAILURE;
        }
        String message = ex.getMessage();
        return message != null && !message.isBlank() ? message : GENERIC_FAILURE;
    }

    /** Same text for unknown account and wrong password, so the response does not reveal which. */
    private static final String GENERIC_FAILURE =
            "Invalid email or password. If your email is not verified yet, use the link in your inbox.";
}
