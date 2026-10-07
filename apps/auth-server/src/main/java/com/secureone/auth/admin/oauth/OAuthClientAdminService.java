package com.secureone.auth.admin.oauth;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationCreateRequest;
import com.secureone.auth.admin.application.ApplicationAdminService;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateResult;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientSecretResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientUpdateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthEndpoints;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OAuthClientAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthClientRepository oauthClients;
    private final ApplicationRepository applications;
    private final TenantRepository tenants;
    private final ApplicationAdminService applicationAdmin;
    private final AuditService auditService;
    private final EmailNotificationService emailService;
    private final String issuerBaseUrl;

    public OAuthClientAdminService(
            OAuthClientRepository oauthClients,
            ApplicationRepository applications,
            TenantRepository tenants,
            @Lazy ApplicationAdminService applicationAdmin,
            AuditService auditService,
            EmailNotificationService emailService,
            @Value("${secureone.issuer:http://localhost:9000}") String issuerBaseUrl) {
        this.oauthClients = oauthClients;
        this.applications = applications;
        this.tenants = tenants;
        this.applicationAdmin = applicationAdmin;
        this.auditService = auditService;
        this.emailService = emailService;
        this.issuerBaseUrl = issuerBaseUrl.endsWith("/") ? issuerBaseUrl.substring(0, issuerBaseUrl.length() - 1) : issuerBaseUrl;
    }

    @Transactional(readOnly = true)
    public List<OAuthClientResponse> list(UUID tenantId, UUID applicationId) {
        List<OAuthClient> rows;
        if (applicationId != null) {
            rows = oauthClients.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        } else if (tenantId != null) {
            rows = oauthClients.findAll().stream()
                    .filter(c -> applicationTenantId(c.getApplicationId()).equals(tenantId))
                    .toList();
        } else {
            rows = oauthClients.findAll();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OAuthClientResponse get(UUID id) {
        return toResponse(require(id));
    }

    public OAuthClientCreateResult create(OAuthClientCreateRequest request) {
        Application app = resolveOrCreateApplication(request);
        String clientId = resolveClientId(request.clientId(), app);
        if (oauthClients.existsByClientId(clientId)) {
            throw new ConflictException("OAuth client_id already exists: " + clientId);
        }
        String type = normalizeType(request.type());
        String plainSecret = null;
        OAuthClient client = new OAuthClient();
        client.setApplicationId(app.getId());
        client.setClientId(clientId);
        client.setClientName(app.getName());
        client.setType(type);
        client.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        client.setGrantTypes(nonEmptyList(request.grantTypes(), defaultGrantTypes(type)));
        client.setScopes(nonEmptyList(request.scopes(), defaultScopes(type)));
        client.setRedirectUris(RedirectUriValidator.validate(request.redirectUris(), "redirectUri"));
        client.setPostLogoutRedirectUris(
                RedirectUriValidator.validate(request.postLogoutRedirectUris(), "postLogoutRedirectUri"));
        client.setRequirePkce(request.pkceRequired() != null ? request.pkceRequired() : defaultPkceRequired(type));
        client.setTokenEndpointAuthMethod(
                request.tokenEndpointAuthMethod() != null && !request.tokenEndpointAuthMethod().isBlank()
                        ? request.tokenEndpointAuthMethod()
                        : defaultAuthMethod(type));
        if (isConfidential(type)) {
            plainSecret = generateClientSecret();
            client.setClientSecret(plainSecret);
        }
        oauthClients.save(client);
        auditService.record(
                app.getTenantId(),
                "admin",
                "oauth_client.created",
                "oauth_client",
                client.getId(),
                client.getClientId(),
                true);
        emailService.sendAdminNotification(
                app.getId(), "OAuth client registered", "New OAuth client: " + client.getClientId());
        return new OAuthClientCreateResult(toResponse(client), plainSecret);
    }

    public OAuthClientResponse update(UUID id, OAuthClientUpdateRequest request) {
        OAuthClient client = require(id);
        Application app = requireApplication(client.getApplicationId());
        String type = normalizeType(request.type() != null ? request.type() : client.getType());
        client.setType(type);
        client.setStatus(normalizeStatus(request.status(), client.getStatus()));
        if (request.grantTypes() != null) client.setGrantTypes(request.grantTypes());
        if (request.scopes() != null) client.setScopes(request.scopes());
        if (request.redirectUris() != null) {
            client.setRedirectUris(RedirectUriValidator.validate(request.redirectUris(), "redirectUri"));
        }
        if (request.postLogoutRedirectUris() != null) {
            client.setPostLogoutRedirectUris(
                    RedirectUriValidator.validate(request.postLogoutRedirectUris(), "postLogoutRedirectUri"));
        }
        if (request.pkceRequired() != null) client.setRequirePkce(request.pkceRequired());
        if (request.tokenEndpointAuthMethod() != null) {
            client.setTokenEndpointAuthMethod(request.tokenEndpointAuthMethod());
        }
        client.setClientName(app.getName());
        auditService.record(
                app.getTenantId(),
                "admin",
                "oauth_client.updated",
                "oauth_client",
                client.getId(),
                client.getClientId(),
                true);
        return toResponse(client);
    }

    public OAuthClientSecretResponse rotateClientSecret(UUID id) {
        OAuthClient client = require(id);
        if (!isConfidential(client.getType())) {
            throw new IllegalArgumentException("Public clients (SPA/native) do not use a client secret.");
        }
        String plainSecret = generateClientSecret();
        client.setClientSecret(plainSecret);
        Application app = requireApplication(client.getApplicationId());
        auditService.record(
                app.getTenantId(),
                "admin",
                "oauth_client.secret_rotated",
                "oauth_client",
                client.getId(),
                client.getClientId(),
                true);
        return new OAuthClientSecretResponse(plainSecret);
    }

    public void delete(UUID id) {
        OAuthClient client = require(id);
        Application app = requireApplication(client.getApplicationId());
        oauthClients.delete(client);
        auditService.record(
                app.getTenantId(),
                "admin",
                "oauth_client.deleted",
                "oauth_client",
                id,
                client.getClientId(),
                true);
    }

    private Application resolveOrCreateApplication(OAuthClientCreateRequest request) {
        if (request.applicationId() != null) {
            return requireApplication(request.applicationId());
        }
        if (request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required when applicationId is not provided.");
        }
        String name = request.applicationName();
        if (name == null || name.isBlank()) {
            name = request.clientId() != null && !request.clientId().isBlank()
                    ? request.clientId().trim()
                    : "Application";
        }
        return applications
                .findById(applicationAdmin.create(new ApplicationCreateRequest(
                                request.tenantId(), name.trim(), null, null, "active"))
                        .id())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found after auto-create"));
    }

    private OAuthClient require(UUID id) {
        return oauthClients
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OAuth client not found: " + id));
    }

    private Application requireApplication(UUID id) {
        return applications
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private UUID applicationTenantId(UUID applicationId) {
        return requireApplication(applicationId).getTenantId();
    }

    private OAuthClientResponse toResponse(OAuthClient client) {
        Application app = requireApplication(client.getApplicationId());
        Tenant tenant = tenants.findById(app.getTenantId()).orElse(null);
        String type = client.getType() != null ? client.getType() : "web";
        boolean confidential = isConfidential(type);
        return new OAuthClientResponse(
                client.getId(),
                client.getApplicationId(),
                app.getTenantId(),
                app.getName(),
                client.getClientId(),
                type,
                client.getStatus().toLowerCase(Locale.ROOT),
                copyList(client.getGrantTypes()),
                copyList(client.getScopes()),
                copyList(client.getRedirectUris()),
                copyList(client.getPostLogoutRedirectUris()),
                confidential,
                client.isRequirePkce(),
                client.getTokenEndpointAuthMethod(),
                confidential && client.getClientSecret() != null && !client.getClientSecret().isBlank(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                oAuthEndpoints());
    }

    private OAuthEndpoints oAuthEndpoints() {
        return new OAuthEndpoints(
                issuerBaseUrl,
                issuerBaseUrl + "/oauth2/authorize",
                issuerBaseUrl + "/oauth2/token",
                issuerBaseUrl + "/oauth2/jwks");
    }

    private static String resolveClientId(String requested, Application app) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return app.getSlug();
    }

    private static List<String> nonEmptyList(List<String> values, List<String> defaults) {
        return values != null && !values.isEmpty() ? values : defaults;
    }

    private static List<String> copyList(List<String> values) {
        return values != null ? new ArrayList<>(values) : List.of();
    }

    private static List<String> defaultGrantTypes(String type) {
        return switch (type) {
            case "m2m" -> List.of("client_credentials");
            default -> List.of("authorization_code", "refresh_token");
        };
    }

    private static List<String> defaultScopes(String type) {
        return "m2m".equals(type) ? List.of() : List.of("openid", "profile", "email");
    }

    private static boolean defaultPkceRequired(String type) {
        return !"m2m".equals(type);
    }

    private static String defaultAuthMethod(String type) {
        return isConfidential(type) ? "client_secret_basic" : "none";
    }

    private static boolean isConfidential(String type) {
        return "web".equals(type) || "m2m".equals(type);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "web";
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "spa", "native", "m2m", "web" -> type.trim().toLowerCase(Locale.ROOT);
            default -> "web";
        };
    }

    private static String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) return defaultStatus;
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> "ACTIVE";
            case "disabled" -> "DISABLED";
            case "suspended" -> "SUSPENDED";
            default -> status.trim().toUpperCase(Locale.ROOT);
        };
    }

    private static String generateClientSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
