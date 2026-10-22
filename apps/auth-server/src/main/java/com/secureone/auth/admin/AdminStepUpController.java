package com.secureone.auth.admin;

import com.secureone.auth.mfa.MfaRecoveryCodeService;
import com.secureone.auth.mfa.TotpMfaService;
import com.secureone.auth.mfa.passkey.PasskeyAuthenticationService;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in operator verifies again (code, backup code or passkey) before a sensitive console action. */
@Tag(name = "Admin — operator profile", description = "Signed-in operator: verify it's you again")
@RestController
@RequestMapping("/api/admin/v1/me/step-up")
public class AdminStepUpController {

    public record StepUpRequest(String code, String recoveryCode) {}

    public record PasskeyStepUpRequest(UUID requestId, String credential) {}

    private final AdminOperatorService operators;
    private final AdminStepUpService stepUp;
    private final TotpMfaService totp;
    private final MfaRecoveryCodeService recoveryCodes;
    private final PasskeyAuthenticationService passkeys;
    private final com.secureone.auth.mfa.MfaPolicyService mfaPolicy;

    public AdminStepUpController(
            AdminOperatorService operators,
            AdminStepUpService stepUp,
            TotpMfaService totp,
            MfaRecoveryCodeService recoveryCodes,
            PasskeyAuthenticationService passkeys,
            com.secureone.auth.mfa.MfaPolicyService mfaPolicy) {
        this.operators = operators;
        this.stepUp = stepUp;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.passkeys = passkeys;
        this.mfaPolicy = mfaPolicy;
    }

    @Operation(summary = "Whether the operator verified recently, and with which methods they can")
    @GetMapping
    public Map<String, Object> status(Authentication authentication, HttpServletRequest request,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs) {
        UserAccount user = operator(authentication, actAs);
        return Map.of(
                "verifiedRecently", stepUp.isFresh(request),
                "passkey", mfaPolicy.hasVerifiedPasskey(user.getId()),
                "totp", mfaPolicy.hasVerifiedTotp(user.getId()));
    }

    @Operation(summary = "Verify it's you with an authenticator code or a recovery code")
    @PostMapping
    public Map<String, Object> verify(Authentication authentication, HttpServletRequest request,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @RequestBody StepUpRequest body) {
        UserAccount user = operator(authentication, actAs);
        boolean ok = body.recoveryCode() != null && !body.recoveryCode().isBlank()
                ? recoveryCodes.verifyAndConsume(user.getId(), body.recoveryCode().trim())
                : body.code() != null && totp.verifyForUser(user.getId(), body.code());
        return done(request, ok);
    }

    @Operation(summary = "Verify it's you with a passkey or security key: options")
    @PostMapping("/passkey/options")
    public Map<String, Object> passkeyOptions(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs) {
        UserAccount user = operator(authentication, actAs);
        String principal = authentication.getName();
        String slug = principal.contains(":") ? principal.substring(0, principal.indexOf(':')) : "platform";
        return passkeys.beginSecondStep(slug, user.getEmail());
    }

    @Operation(summary = "Verify it's you with a passkey or security key")
    @PostMapping("/passkey/verify")
    public Map<String, Object> passkeyVerify(Authentication authentication, HttpServletRequest request,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @RequestBody PasskeyStepUpRequest body) {
        UserAccount user = operator(authentication, actAs);
        boolean ok = body.requestId() != null && passkeys.finishSecondStep(body.requestId(), body.credential(), user.getId());
        return done(request, ok);
    }

    private Map<String, Object> done(HttpServletRequest request, boolean ok) {
        if (!ok) {
            // 400, not 401: the console treats 401 as "session ended" and would sign the operator out.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is not valid.");
        }
        stepUp.mark(request);
        return Map.of("verified", true);
    }

    private UserAccount operator(Authentication authentication, String actAs) {
        if (operators.isBreakGlass(authentication)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The emergency account has no second step.");
        }
        // Always the real operator, never the one acted as: it is their own second step.
        return operators.resolveTenantUser(authentication, null)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
    }
}
