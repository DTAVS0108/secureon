package com.secureone.auth.admin.settings;

import com.secureone.auth.admin.AdminStepUpService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.config.oauth.OAuth2SigningKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform settings › OAuth tokens › Signing keys: rotate the key that signs tokens, like Auth0's "Rotate signing
 * key". The next key is always published in JWKS ahead of time, so apps that cache JWKS already have it; the old
 * key stays published until it is retired (after the tokens it signed have expired).
 */
@Tag(name = "Admin — signing keys", description = "Rotate the OAuth/OIDC token signing key")
@RestController
public class SigningKeyController {

    private final OAuth2SigningKeyService keys;
    private final AdminStepUpService stepUp;
    private final AuditService audit;

    public SigningKeyController(OAuth2SigningKeyService keys, AdminStepUpService stepUp, AuditService audit) {
        this.keys = keys;
        this.stepUp = stepUp;
        this.audit = audit;
    }

    @GetMapping("/api/admin/v1/settings/signing-keys")
    public Map<String, Object> status() {
        return keys.status();
    }

    @PostMapping("/api/admin/v1/settings/signing-keys/rotate")
    public Map<String, Object> rotate(HttpServletRequest request) {
        stepUp.require(request);
        String before = String.valueOf(keys.status().get("currentKid"));
        Map<String, Object> after = keys.rotate();
        audit.record(null, "admin", "oauth.signing_key_rotated", "signing_key", null,
                before + " → " + after.get("currentKid"), true);
        return after;
    }

    @PostMapping("/api/admin/v1/settings/signing-keys/retire-previous")
    public Map<String, Object> retirePrevious(HttpServletRequest request) {
        stepUp.require(request);
        String previous = String.valueOf(keys.status().get("previousKid"));
        Map<String, Object> after = keys.retirePrevious();
        audit.record(null, "admin", "oauth.signing_key_retired", "signing_key", null, previous, true);
        return after;
    }
}
