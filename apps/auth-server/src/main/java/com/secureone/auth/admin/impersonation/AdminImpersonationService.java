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
    /** The console opens the link at once; a short life limits a leaked link. */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(2);

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
        if (applicationId != null && !isMember(applicationId, user.getId())) {
            // Signing in as the user must not change what they can use.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This user is not a member of the application. Add them first, or impersonate without an application.");
        }
        String why = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        UUID token = UUID.randomUUID();
        ImpersonationPayload payload = new ImpersonationPayload(
                user.getId(), user.getTenantId(), adminPrincipal, why, applicationId);
        redis.opsForValue().set(TOKEN_PREFIX + token, serialize(payload), TOKEN_TTL);
        audit.record(
                user.getTenantId(),
                applicationId,
                adminPrincipal,
                "user.impersonate",
                "user",
                user.getId(),
                user.getEmail() + " — reason: " + why,
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
        UserDetails details = userDetailsService.buildSessionPrincipal(tenant.getSlug(), user);
        // The marker travels with the sign-in, so tokens issued from it name the administrator ("act").
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                new java.util.ArrayList<>(details.getAuthorities());
        authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                AuthContextAttributes.IMPERSONATED_BY_AUTHORITY + payload.adminPrincipal()));
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, authorities);
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
                user.getEmail() + " — reason: " + payload.reason(),
                true);
    }

    /** Memberships live in the application's own schema (the admin request is not switched into it). */
    private boolean isMember(UUID applicationId, UUID userId) {
        if (jdbc == null) {
            return memberships.isMember(applicationId, userId);
        }
        return Boolean.TRUE.equals(com.secureone.auth.tenant.RlsScope.asPlatform(() -> {
            java.util.List<String> schemas = jdbc.queryForList(
                    "SELECT schema_name FROM platform.application WHERE id = ?", String.class, applicationId);
            String schema = schemas.isEmpty() || schemas.get(0) == null ? "platform" : schemas.get(0);
            if (!schema.matches("[a-z][a-z0-9_]{0,62}")) {
                return false;
            }
            Integer n = jdbc.queryForObject("SELECT count(*) FROM \"" + schema + "\".user_application"
                    + " WHERE user_id = ? AND application_id = ?", Integer.class, userId, applicationId);
            return n != null && n > 0;
        }));
    }

    private void assertImpersonationAllowed(UserAccount user, String adminPrincipal) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot impersonate a disabled user.");
        }
        // Administrators are never impersonated: that would borrow their access, not look at a user's view.
        Integer privileged = jdbc == null ? 0 : com.secureone.auth.tenant.RlsScope.asPlatform(() -> jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM platform.platform_operator WHERE user_id = ?)"
                        + " + (SELECT count(*) FROM platform.admin_console_access WHERE user_id = ?)",
                Integer.class, user.getId(), user.getId()));
        if (privileged != null && privileged > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot impersonate an administrator (platform operator or console admin).");
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
