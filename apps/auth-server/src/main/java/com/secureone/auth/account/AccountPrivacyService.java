package com.secureone.auth.account;

import com.secureone.auth.audit.AuditLog;
import com.secureone.auth.audit.AuditLogRepository;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.federation.UserIdentityRepository;
import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.mfa.MfaFactor;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountPrivacyService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final TenantUserDetailsService userDetails;
    private final MfaFactorRepository mfaFactors;
    private final UserIdentityRepository identities;
    private final AuditLogRepository auditLogs;

    public AccountPrivacyService(
            UserAccountRepository users,
            TenantRepository tenants,
            TenantUserDetailsService userDetails,
            MfaFactorRepository mfaFactors,
            UserIdentityRepository identities,
            AuditLogRepository auditLogs) {
        this.users = users;
        this.tenants = tenants;
        this.userDetails = userDetails;
        this.mfaFactors = mfaFactors;
        this.identities = identities;
        this.auditLogs = auditLogs;
    }

    public Map<String, Object> export(Jwt jwt) {
        return exportForUser(resolveUser(jwt));
    }

    public Map<String, String> requestDeletion(Jwt jwt) {
        return requestDeletionForUser(resolveUser(jwt));
    }

    public Map<String, Object> exportForUser(UserAccount user) {
        Tenant tenant = tenants.findById(user.getTenantId()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("userId", user.getId());
        out.put("email", user.getEmail());
        out.put("emailVerified", user.isEmailVerified());
        out.put("displayName", user.getDisplayName());
        out.put("status", user.getStatus());
        out.put("createdAt", user.getCreatedAt());
        out.put("updatedAt", user.getUpdatedAt());
        out.put("lastLoginAt", user.getLastLoginAt());
        if (tenant != null) {
            out.put("tenantId", tenant.getId());
            out.put("tenantSlug", tenant.getSlug());
            out.put("tenantName", tenant.getName());
        }
        List<Map<String, Object>> factors = new ArrayList<>();
        for (MfaFactor factor : mfaFactors.findByUserIdOrderByCreatedAtAsc(user.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", factor.getId());
            row.put("type", factor.getType());
            row.put("verified", factor.isVerified());
            row.put("createdAt", factor.getCreatedAt());
            factors.add(row);
        }
        out.put("mfaFactors", factors);
        out.put("linkedIdentityCount", identities.findByUserId(user.getId()).size());
        return out;
    }

    public Map<String, String> requestDeletionForUser(UserAccount user) {
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            return Map.of(
                    "message",
                    "Your account is already disabled. Contact your administrator to complete removal.");
        }
        user.setStatus("DISABLED");
        users.save(user);
        recordUserAudit(user, "account.deletion_requested", true);
        return Map.of(
                "message",
                "Account deletion requested. Your account has been disabled; an administrator may complete removal.");
    }

    private void recordUserAudit(UserAccount user, String action, boolean success) {
        AuditLog log = new AuditLog();
        log.setTenantId(user.getTenantId());
        log.setActorType("USER");
        log.setActorId(user.getId());
        log.setAction(action);
        log.setTargetType("USER");
        log.setTargetId(user.getId());
        log.setIp(ClientIpResolver.fromMdcOrUnknown());
        Map<String, Object> meta = new HashMap<>();
        meta.put("result", success ? "success" : "failure");
        meta.put("actorEmail", user.getEmail());
        log.setMetadata(meta);
        auditLogs.save(log);
    }

    private UserAccount resolveUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("Token subject is missing.");
        }
        try {
            UUID id = UUID.fromString(subject);
            return users.findById(id).orElseThrow(() -> new BadCredentialsException("User not found."));
        } catch (IllegalArgumentException ignored) {
            // Fall through — subject is tenantSlug:email
        }
        int sep = subject.indexOf(':');
        if (sep <= 0) {
            throw new BadCredentialsException("Unrecognized token subject.");
        }
        String tenantSlug = subject.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = subject.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return userDetails.resolveAccount(tenantSlug, email);
    }
}
