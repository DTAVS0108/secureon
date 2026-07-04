package com.secureone.auth.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class IdentityProviderService {

    private final IdentityProviderRepository providers;
    private final TenantRepository tenants;
    private final ApplicationRepository applications;
    private final SecretProtector secrets;
    private final RestClient http;
    private final ObjectMapper mapper;

    @Value("${secureone.public-base-url:http://localhost:9000}")
    private String publicBaseUrl;

    public IdentityProviderService(
            IdentityProviderRepository providers,
            TenantRepository tenants,
            ApplicationRepository applications,
            SecretProtector secrets,
            ObjectMapper mapper) {
        this.providers = providers;
        this.tenants = tenants;
        this.applications = applications;
        this.secrets = secrets;
        this.mapper = mapper;
        this.http = RestClient.builder().build();
    }

    public List<Map<String, Object>> listPlatform(UUID tenantId) {
        return providers.findByTenantIdAndApplicationIdIsNullOrderByAliasAsc(tenantId).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    public List<Map<String, Object>> listForApplication(UUID tenantId, UUID applicationId) {
        requireApplication(tenantId, applicationId);
        return providers.findByTenantIdAndApplicationIdOrderByAliasAsc(tenantId, applicationId).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    public Map<String, Object> create(UUID tenantId, UUID applicationId, Map<String, Object> body) {
        requireTenant(tenantId);
        if (applicationId != null) {
            requireApplication(tenantId, applicationId);
        }
        FederationProviderType type = FederationProviderType.parse(String.valueOf(body.get("type")));
        if (type == FederationProviderType.SAML) {
            throw new IllegalArgumentException("SAML providers are not yet supported for login.");
        }
        String alias = normalizeAlias(String.valueOf(body.get("alias")));
        assertAliasAvailable(tenantId, applicationId, alias);
        IdentityProvider row = new IdentityProvider();
        row.setTenantId(tenantId);
        row.setApplicationId(applicationId);
        row.setAlias(alias);
        row.setType(type.name());
        row.setDisplayName(stringOrDefault(body.get("displayName"), alias));
        row.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        row.setConfig(sanitizeConfig(type, Map.of(), body));
        return toAdminResponse(providers.save(row));
    }

    public Map<String, Object> update(UUID tenantId, UUID id, Map<String, Object> body) {
        IdentityProvider row = requireOwned(tenantId, id);
        if (body.containsKey("displayName")) {
            row.setDisplayName(String.valueOf(body.get("displayName")));
        }
        if (body.containsKey("enabled")) {
            row.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.containsKey("config") || body.containsKey("clientId") || body.containsKey("clientSecret")) {
            FederationProviderType type = FederationProviderType.parse(row.getType());
            row.setConfig(sanitizeConfig(type, row.getConfig(), body));
        }
        return toAdminResponse(providers.save(row));
    }

    public void delete(UUID tenantId, UUID id) {
        IdentityProvider row = requireOwned(tenantId, id);
        providers.delete(row);
    }

    public List<Map<String, Object>> listPublic(UUID tenantId, UUID applicationId) {
        List<IdentityProvider> rows = new ArrayList<>();
        rows.addAll(providers.findByTenantIdAndApplicationIdIsNullOrderByAliasAsc(tenantId));
        if (applicationId != null) {
            rows.addAll(providers.findByTenantIdAndApplicationIdOrderByAliasAsc(tenantId, applicationId));
        }
        return rows.stream()
                .filter(IdentityProvider::isEnabled)
                .filter(p -> FederationProviderType.parse(p.getType()) != FederationProviderType.SAML)
                .map(this::toPublicResponse)
                .toList();
    }

    public IdentityProvider requireEnabledProvider(UUID tenantId, UUID applicationId, String alias) {
        IdentityProvider provider = resolveProvider(tenantId, applicationId, alias)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Federation provider not found."));
        if (!provider.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Federation provider is disabled.");
        }
        FederationProviderType type = FederationProviderType.parse(provider.getType());
        if (type == FederationProviderType.SAML) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SAML login is not yet available.");
        }
        return provider;
    }

    public String buildAuthorizationRedirect(IdentityProvider provider, String state) {
        Map<String, Object> config = provider.getConfig();
        String clientId = string(config.get("clientId"));
        if (clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider clientId is not configured.");
        }
        String redirectUri = callbackUrl(provider.getAlias());
        String authUri = string(config.get("authorizationUri"));
        String scopes = stringOrDefault(config.get("scopes"), "openid email profile");
        StringBuilder url = new StringBuilder(authUri);
        url.append(authUri.contains("?") ? "&" : "?");
        url.append("client_id=").append(enc(clientId));
        url.append("&redirect_uri=").append(enc(redirectUri));
        url.append("&response_type=code");
        url.append("&scope=").append(enc(scopes.replace(",", " ")));
        url.append("&state=").append(enc(state));
        if (FederationProviderType.GOOGLE.name().equals(provider.getType())) {
            url.append("&access_type=online&prompt=select_account");
        }
        return url.toString();
    }

    public FederationUserProfile exchangeCode(IdentityProvider provider, String code) {
        Map<String, Object> config = provider.getConfig();
        String clientId = string(config.get("clientId"));
        String clientSecret = decryptSecret(string(config.get("clientSecretEncrypted")));
        String tokenUri = string(config.get("tokenUri"));
        String redirectUri = callbackUrl(provider.getAlias());
        String tokenBody = http.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(buildTokenRequestBody(provider.getType(), clientId, clientSecret, code, redirectUri))
                .retrieve()
                .body(String.class);
        String accessToken = parseAccessToken(tokenBody);
        return fetchProfile(provider, accessToken);
    }

    public String callbackUrl(String alias) {
        return publicBaseUrl + "/api/v1/auth/federation/" + alias + "/callback";
    }

    public String decryptSecret(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        return secrets.decrypt(encrypted);
    }

    private FederationUserProfile fetchProfile(IdentityProvider provider, String accessToken) {
        Map<String, Object> config = provider.getConfig();
        FederationProviderType type = FederationProviderType.parse(provider.getType());
        if (type == FederationProviderType.GITHUB) {
            return fetchGitHubProfile(config, accessToken);
        }
        String userInfoUri = string(config.get("userInfoUri"));
        String json = http.get()
                .uri(userInfoUri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = mapper.readTree(json);
            String subject = node.path("sub").asText(null);
            if (subject == null || subject.isBlank()) {
                subject = node.path("id").isNumber() ? node.path("id").asText() : text(node, "id", null);
            }
            String email = text(node, "email", null);
            if (email == null || email.isBlank()) {
                email = text(node, "preferred_username", null);
            }
            return new FederationUserProfile(subject, email, json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse user profile from IdP.", ex);
        }
    }

    private FederationUserProfile fetchGitHubProfile(Map<String, Object> config, String accessToken) {
        String userJson = http.get()
                .uri(string(config.get("userInfoUri")))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(String.class);
        String emailJson = http.get()
                .uri(string(config.get("emailUri")))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(String.class);
        try {
            JsonNode user = mapper.readTree(userJson);
            String subject = user.path("id").isNumber() ? user.path("id").asText() : text(user, "id", null);
            String email = resolveGitHubEmail(emailJson);
            return new FederationUserProfile(subject, email, userJson);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse GitHub profile.", ex);
        }
    }

    private String resolveGitHubEmail(String emailJson) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode emails = mapper.readTree(emailJson);
        if (!emails.isArray()) {
            return null;
        }
        for (JsonNode item : emails) {
            if (item.path("primary").asBoolean(false) && item.path("verified").asBoolean(false)) {
                return item.path("email").asText(null);
            }
        }
        for (JsonNode item : emails) {
            if (item.path("verified").asBoolean(false)) {
                return item.path("email").asText(null);
            }
        }
        return null;
    }

    private String parseAccessToken(String tokenBody) {
        try {
            JsonNode node = mapper.readTree(tokenBody);
            String token = node.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Token response missing access_token.");
            }
            return token;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse token response.", ex);
        }
    }

    private static String buildTokenRequestBody(
            String type, String clientId, String clientSecret, String code, String redirectUri) {
        if (FederationProviderType.GITHUB.name().equals(type)) {
            return "client_id="
                    + enc(clientId)
                    + "&client_secret="
                    + enc(clientSecret)
                    + "&code="
                    + enc(code)
                    + "&redirect_uri="
                    + enc(redirectUri);
        }
        return "grant_type=authorization_code&client_id="
                + enc(clientId)
                + "&client_secret="
                + enc(clientSecret)
                + "&code="
                + enc(code)
                + "&redirect_uri="
                + enc(redirectUri);
    }

    private Map<String, Object> sanitizeConfig(
            FederationProviderType type, Map<String, Object> current, Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(FederationPresets.defaultsFor(type));
        if (current != null) {
            current.forEach((k, v) -> {
                if (v != null) {
                    out.put(k, v);
                }
            });
        }
        Object configRaw = body.get("config");
        if (configRaw instanceof Map<?, ?> configMap) {
            configMap.forEach((k, v) -> {
                if (v != null && !"clientSecret".equals(String.valueOf(k))) {
                    out.put(String.valueOf(k), v);
                }
            });
        }
        if (body.get("clientId") != null) {
            out.put("clientId", String.valueOf(body.get("clientId")).trim());
        }
        if (body.get("clientSecret") instanceof String secret && !secret.isBlank()) {
            out.put("clientSecretEncrypted", secrets.encrypt(secret.trim()));
        }
        if (body.containsKey("jitProvisioning")) {
            out.put("jitProvisioning", Boolean.TRUE.equals(body.get("jitProvisioning")));
        }
        if (body.containsKey("linkByEmail")) {
            out.put("linkByEmail", Boolean.TRUE.equals(body.get("linkByEmail")));
        }
        out.remove("clientSecret");
        return out;
    }

    private Map<String, Object> toAdminResponse(IdentityProvider row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId().toString());
        out.put("tenantId", row.getTenantId().toString());
        out.put("applicationId", row.getApplicationId() != null ? row.getApplicationId().toString() : null);
        out.put("alias", row.getAlias());
        out.put("type", row.getType());
        out.put("displayName", row.getDisplayName());
        out.put("enabled", row.isEnabled());
        out.put("authMethodId", FederationPresets.authMethodId(FederationProviderType.parse(row.getType())));
        Map<String, Object> publicConfig = new LinkedHashMap<>(row.getConfig());
        publicConfig.remove("clientSecretEncrypted");
        publicConfig.put("clientSecretConfigured", hasSecret(row.getConfig()));
        out.put("config", publicConfig);
        out.put("callbackUrl", callbackUrl(row.getAlias()));
        return out;
    }

    private Map<String, Object> toPublicResponse(IdentityProvider row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alias", row.getAlias());
        out.put("type", row.getType());
        out.put("displayName", row.getDisplayName());
        out.put("authMethodId", FederationPresets.authMethodId(FederationProviderType.parse(row.getType())));
        out.put("startUrl", "/api/v1/auth/federation/" + row.getAlias() + "/start");
        return out;
    }

    private java.util.Optional<IdentityProvider> resolveProvider(UUID tenantId, UUID applicationId, String alias) {
        if (applicationId != null) {
            return providers
                    .findByTenantIdAndApplicationIdAndAliasIgnoreCase(tenantId, applicationId, alias)
                    .or(() -> providers.findByTenantIdAndApplicationIdIsNullAndAliasIgnoreCase(tenantId, alias));
        }
        return providers.findByTenantIdAndApplicationIdIsNullAndAliasIgnoreCase(tenantId, alias);
    }

    private IdentityProvider requireOwned(UUID tenantId, UUID id) {
        IdentityProvider row = providers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!tenantId.equals(row.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return row;
    }

    private void assertAliasAvailable(UUID tenantId, UUID applicationId, String alias) {
        if (resolveProvider(tenantId, applicationId, alias).isPresent()) {
            throw new IllegalArgumentException("Provider alias already exists: " + alias);
        }
    }

    private void requireTenant(UUID tenantId) {
        tenants.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found."));
    }

    private Application requireApplication(UUID tenantId, UUID applicationId) {
        Application app = applications.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
        if (!tenantId.equals(app.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found.");
        }
        return app;
    }

    private static String normalizeAlias(String alias) {
        String value = alias.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9_-]{1,62}")) {
            throw new IllegalArgumentException("Alias must be 2-63 chars: lowercase letters, numbers, _ or -.");
        }
        return value;
    }

    private static boolean hasSecret(Map<String, Object> config) {
        return config != null && !string(config.get("clientSecretEncrypted")).isBlank();
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }

    private static String stringOrDefault(Object raw, String fallback) {
        String value = string(raw);
        return value.isBlank() ? fallback : value;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text != null && !text.isBlank() ? text : fallback;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record FederationUserProfile(String subject, String email, String rawProfileJson) {}
}
