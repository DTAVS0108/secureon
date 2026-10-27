package com.secureone.auth.config;

import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Enriches OIDC {@code /userinfo} for integrated apps: email claims with the {@code email} scope, name claims with
 * {@code profile}, and always the tenant, user id, roles and permissions the access token already carries.
 */
@Configuration
public class OidcUserInfoConfig {

    @Bean
    Function<OidcUserInfoAuthenticationContext, OidcUserInfo> oidcUserInfoMapper(
            UserAccountRepository users, TenantRepository tenants) {
        return context -> buildUserInfo(context, users, tenants);
    }

    private static OidcUserInfo buildUserInfo(
            OidcUserInfoAuthenticationContext context,
            UserAccountRepository users,
            TenantRepository tenants) {
        OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
        Object principal = authentication.getPrincipal();
        String subject;
        if (principal instanceof JwtAuthenticationToken jwtAuth) {
            subject = jwtAuth.getToken().getSubject();
        } else if (principal instanceof org.springframework.security.core.Authentication auth) {
            subject = auth.getName();
        } else {
            subject = String.valueOf(principal);
        }

        OidcUserInfo.Builder builder = OidcUserInfo.builder().subject(subject);
        // OIDC Core 5.4: email claims only with the "email" scope, name claims only with "profile".
        Set<String> scopes = principal instanceof JwtAuthenticationToken jwtAuth ? scopes(jwtAuth.getToken()) : Set.of();
        boolean email = scopes.contains("email");
        boolean profile = scopes.contains("profile");
        if (principal instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            if (jwt.hasClaim("amr")) {
                builder.claim("amr", jwt.getClaim("amr"));
            }
            if (jwt.hasClaim("acr")) {
                builder.claim("acr", jwt.getClaim("acr"));
            }
            // Same application roles and permissions as the access token carries.
            if (jwt.hasClaim("roles")) {
                builder.claim("roles", jwt.getClaim("roles"));
            }
            if (jwt.hasClaim("permissions")) {
                builder.claim("permissions", jwt.getClaim("permissions"));
            }
        }
        resolveUser(subject, users, tenants).ifPresent(user -> {
            if (email) {
                builder.email(user.getEmail());
                builder.emailVerified(user.isEmailVerified());
            }
            if (profile) {
                if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                    builder.name(user.getDisplayName());
                    builder.preferredUsername(user.getDisplayName());
                } else if (user.getUsername() != null) {
                    builder.preferredUsername(user.getUsername());
                }
                if (user.getGivenName() != null) {
                    builder.givenName(user.getGivenName());
                }
                if (user.getFamilyName() != null) {
                    builder.familyName(user.getFamilyName());
                }
            }
            tenants.findById(user.getTenantId()).ifPresent(tenant -> {
                builder.claim("tenant_id", tenant.getId().toString());
                builder.claim("tenant_slug", tenant.getSlug());
                builder.claim("tenant_name", tenant.getName());
            });
            builder.claim("user_id", user.getId().toString());
            builder.claim("status", user.getStatus());
        });

        if (profile && subject.contains(":")) {
            builder.preferredUsername(subject);
        }
        return builder.build();
    }

    /** The access token's scopes ("scope" is a list in our tokens, a space-separated string in some others). */
    static Set<String> scopes(Jwt jwt) {
        Object raw = jwt.getClaim("scope");
        if (raw instanceof java.util.Collection<?> list) {
            Set<String> out = new java.util.HashSet<>();
            list.forEach(s -> out.add(String.valueOf(s)));
            return out;
        }
        if (raw instanceof String s && !s.isBlank()) {
            return new java.util.HashSet<>(java.util.Arrays.asList(s.trim().split("\\s+")));
        }
        return Set.of();
    }

    private static Optional<UserAccount> resolveUser(
            String subject, UserAccountRepository users, TenantRepository tenants) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(subject);
            return users.findById(id);
        } catch (IllegalArgumentException ignored) {
            // tenant:email
        }
        int sep = subject.indexOf(':');
        if (sep <= 0) {
            return Optional.empty();
        }
        String tenantSlug = subject.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = subject.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return tenants.findBySlug(tenantSlug).flatMap(tenant -> users.findByTenantIdAndEmail(tenant.getId(), email));
    }
}
