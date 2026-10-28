package com.secureone.auth.protection;

import com.secureone.auth.audit.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttackProtectionController {

    private static final String PUBLIC_TAG = "Bot protection";
    private static final String ADMIN_TAG = "Admin — attack protection";

    private final AttackProtectionService protection;
    private final AuditService audit;

    public AttackProtectionController(AttackProtectionService protection, AuditService audit) {
        this.protection = protection;
        this.audit = audit;
    }

    /** For the hosted pages: which CAPTCHA to show and where (no secret). */
    @Tag(name = PUBLIC_TAG, description = "Which CAPTCHA the sign-in pages show, and where (no secrets)")
    @GetMapping("/api/v1/attack-protection")
    public Map<String, Object> publicConfig() {
        return protection.publicConfig();
    }

    /** Platform settings › Attack protection (platform super admins; checked by the admin interceptor). */
    @Tag(name = ADMIN_TAG, description = "Bot protection (CAPTCHA) and IP rules")
    @GetMapping("/api/admin/v1/settings/attack-protection")
    public Map<String, Object> get() {
        return protection.getForAdmin();
    }

    @Tag(name = ADMIN_TAG)
    @PutMapping("/api/admin/v1/settings/attack-protection")
    public Map<String, Object> save(@RequestBody Map<String, Object> body, Authentication authentication) {
        Map<String, Object> saved = protection.save(body);
        audit.record(null, authentication != null ? authentication.getName() : "admin",
                "settings.attack_protection_changed", "platform_setting", null, AttackProtectionService.SETTINGS_KEY, true);
        return saved;
    }
}
