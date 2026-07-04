package com.secureone.auth.admin.impersonation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.application.ApplicationMembershipService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.authn.AuthContextAttributes;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminImpersonationService {

    private static final String TOKEN_PREFIX = "impersonation:token:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final TenantUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final ApplicationMembershipService memberships;
    private final AuditService audit;
    private final String platformAdminUsername;

    public AdminImpersonationService(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            UserAccountRepository users,
            TenantRepository tenants,
            TenantUserDetailsService userDetailsService,
            SecurityContextRepository securityContextRepository,
            ApplicationMembershipService memberships,
            AuditService audit,
            @Value("${spring.security.user.name:admin}") String platformAdminUsername) {
        this.redis = redis;
        this.mapper = mapper;
        this.users = users;
        this.tenants = tenants;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.memberships = memberships;
        this.audit = audit;
        this.platformAdminUsername = platformAdminUsername;
    }

    public ImpersonationTokenResponse createToken(
            UUID userId, String adminPrincipal, String reason, UUID applicationId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        assertImpersonationAllowed(user, adminPrincipal);
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required.");
        }
        UUID token = UUID.randomUUID();
        ImpersonationPayload payload = new ImpersonationPayload(
                user.getId(), user.getTenantId(), adminPrincipal, reason.trim(), applicationId);
        redis.opsForValue().set(TOKEN_PREFIX + token, serialize(payload), TOKEN_TTL);
        audit.record(
                user.getTenantId(),
                applicationId,
                adminPrincipal,
                "user.impersonate",
                "user",
                user.getId(),
                user.getEmail(),
                true);
        return new ImpersonationTokenResponse(
                token.toString(),
                "/api/v1/auth/impersonation/consume?token=" + token,
                (int) TOKEN_TTL.toSeconds());
    }

    public void consumeToken(String tokenValue, HttpServletRequest request, HttpServletResponse response) {
        UUID tokenId;
        try {
            tokenId = UUID.fromString(tokenValue);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid impersonation token.");
        }
        String key = TOKEN_PREFIX + tokenId;
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Impersonation token expired or invalid.");
        }
        redis.delete(key);
        ImpersonationPayload payload = deserialize(raw);
        UserAccount user = users.findById(payload.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "User no longer exists."));
        assertImpersonationAllowed(user, payload.adminPrincipal());
        Tenant tenant = tenants.findById(user.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Tenant not found."));
        if (payload.applicationId() != null) {
            memberships.ensureMemberWithDefaultRole(payload.applicationId(), user.getId());
        }
        UserDetails details = userDetailsService.buildSessionPrincipal(tenant.getSlug(), user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        AuthContextAttributes.markImpersonation(request, payload.adminPrincipal());
        audit.record(
                user.getTenantId(),
                payload.applicationId(),
                payload.adminPrincipal(),
                "user.impersonate.consume",
                "user",
                user.getId(),
                user.getEmail(),
                true);
    }

    private void assertImpersonationAllowed(UserAccount user, String adminPrincipal) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot impersonate a disabled user.");
        }
        if (user.getEmail() != null
                && platformAdminUsername.equalsIgnoreCase(user.getEmail().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot impersonate the platform super-admin.");
        }
        if (adminPrincipal != null && adminPrincipal.contains(":")) {
            int sep = adminPrincipal.indexOf(':');
            String email = adminPrincipal.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
            if (user.getEmail() != null && email.equals(user.getEmail().trim().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot impersonate yourself.");
            }
        }
    }

    private String serialize(ImpersonationPayload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize impersonation payload", ex);
        }
    }

    private ImpersonationPayload deserialize(String raw) {
        try {
            return mapper.readValue(raw, ImpersonationPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid impersonation token payload.");
        }
    }

    public record ImpersonationTokenResponse(String token, String consumeUrl, int expiresInSeconds) {}

    record ImpersonationPayload(UUID userId, UUID tenantId, String adminPrincipal, String reason, UUID applicationId) {}
}
