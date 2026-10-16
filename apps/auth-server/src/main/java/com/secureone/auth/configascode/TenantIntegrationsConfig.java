package com.secureone.auth.configascode;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.configascode.ConfigPlan.Step;
import com.secureone.auth.configascode.TenantConfigService.Change;
import com.secureone.auth.configascode.TenantConfigService.CreatedSecret;
import com.secureone.auth.federation.IdentityProviderService;
import com.secureone.auth.scim.ScimTenantTokens;
import com.secureone.auth.serviceaccount.ServiceAccountService;
import com.secureone.auth.webhook.WebhookEndpointService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The tenant's integrations in configuration files: SCIM token, sign-in providers (tenant-wide and per
 * application), webhooks and service accounts with API keys. Secrets come from environment variables
 * named in the file; without one, existing secrets stay and new ones are generated and shown once.
 */
@Component
class TenantIntegrationsConfig {

    private static final Set<String> PROVIDER_TYPES = Set.of("GOOGLE", "GITHUB", "OIDC", "SAML");

    private final ScimTenantTokens scimTokens;
    private final IdentityProviderService providers;
    private final WebhookEndpointService webhooks;
    private final ServiceAccountService serviceAccounts;
    private final ApplicationRepository applications;

    TenantIntegrationsConfig(
            ScimTenantTokens scimTokens,
            IdentityProviderService providers,
            WebhookEndpointService webhooks,
            ServiceAccountService serviceAccounts,
            ApplicationRepository applications) {
        this.scimTokens = scimTokens;
        this.providers = providers;
        this.webhooks = webhooks;
        this.serviceAccounts = serviceAccounts;
        this.applications = applications;
    }

    // ---------------------------------------------------------------- export

    TenantConfig.Scim exportScim(UUID tenantId) {
        return scimTokens.isConfigured(tenantId) ? new TenantConfig.Scim(true, null) : null;
    }

    List<TenantConfig.IdentityProvider> exportProviders(UUID tenantId, UUID applicationId) {
        List<Map<String, Object>> rows = applicationId == null
                ? providers.listPlatform(tenantId)
                : providers.listForApplication(tenantId, applicationId);
        return rows.stream().map(TenantIntegrationsConfig::toProvider).toList();
    }

    List<TenantConfig.Webhook> exportWebhooks(UUID tenantId, Map<UUID, String> appSlugs) {
        return webhooks.list(tenantId).stream()
                .map(w -> new TenantConfig.Webhook(
                        (String) w.get("url"),
                        w.get("applicationId") != null ? appSlugs.get(UUID.fromString(w.get("applicationId").toString())) : null,
                        strings(w.get("events")),
                        (Boolean) w.get("enabled"),
                        null))
                .sorted(Comparator.comparing(TenantConfig.Webhook::url))
                .toList();
    }

    List<TenantConfig.ServiceAccount> exportServiceAccounts(UUID tenantId) {
        List<TenantConfig.ServiceAccount> out = new ArrayList<>();
        for (Map<String, Object> account : serviceAccounts.list(tenantId)) {
            UUID id = UUID.fromString(account.get("id").toString());
            List<TenantConfig.ApiKey> keys = serviceAccounts.listKeys(tenantId, id).stream()
                    .filter(k -> Boolean.TRUE.equals(k.get("active")))
                    .map(k -> new TenantConfig.ApiKey(text(k.get("label")), text(k.get("expiresAt")), null))
                    .sorted(Comparator.comparing(k -> Objects.requireNonNullElse(k.label(), "")))
                    .toList();
            out.add(new TenantConfig.ServiceAccount(
                    (String) account.get("name"), text(account.get("description")), text(account.get("status")), keys));
        }
        return out;
    }

    // ---------------------------------------------------------------- plan

    /**
     * Plans the tenant-level integrations. Steps run after the tenant and applications exist, so ids are
     * read at apply time: {@code tenantId} and {@code appId} (by slug, for applications in the file or
     * already there).
     */
    void planTenant(ConfigPlan plan, TenantConfig config, UUID existingTenantId, Supplier<UUID> tenantId,
            Set<String> appSlugsInFile) {
        String tenantSlug = config.tenant().slug();
        planScim(plan, tenantSlug, config.scim(), existingTenantId, tenantId);
        if (config.identityProviders() != null) {
            planProviders(plan, tenantSlug, config.identityProviders(), existingTenantId, tenantId, false, null, () -> null);
        }
        if (config.webhooks() != null) {
            planWebhooks(plan, config.webhooks(), existingTenantId, tenantId, appSlugsInFile);
        }
        if (config.serviceAccounts() != null) {
            planServiceAccounts(plan, config.serviceAccounts(), existingTenantId, tenantId);
        }
    }

    private void planScim(ConfigPlan plan, String tenantSlug, TenantConfig.Scim spec, UUID existingTenantId, Supplier<UUID> tenantId) {
        if (spec == null) {
            return;
        }
        boolean configured = existingTenantId != null && scimTokens.isConfigured(existingTenantId);
        if (Boolean.FALSE.equals(spec.enabled())) {
            if (spec.tokenEnv() != null) {
                plan.errors.add("scim: tokenEnv is set but enabled is false");
            }
            if (configured) {
                plan.deletions.add(new Step(new Change("delete", "scim-token", tenantSlug, List.of("SCIM clients can no longer connect")),
                        () -> scimTokens.revoke(tenantId.get())));
            }
            return;
        }
        String token = plan.secret(spec.tokenEnv(), "scim", "scim_", 40);
        if (token != null) {
            if (!configured || !scimTokens.matches(existingTenantId, token)) {
                plan.second.add(new Step(new Change(configured ? "update" : "create", "scim-token", tenantSlug,
                                List.of("token: set from " + spec.tokenEnv())),
                        () -> scimTokens.set(tenantId.get(), token)));
            }
        } else if (spec.tokenEnv() == null && !configured) {
            plan.second.add(new Step(new Change("create", "scim-token", tenantSlug, List.of("token: generated, shown once")),
                    () -> plan.secrets.add(new CreatedSecret("scim-token", tenantSlug, scimTokens.issue(tenantId.get())))));
        }
    }

    /** Sign-in providers of the tenant (applicationId null) or of one application. */
    void planProviders(ConfigPlan plan, String where, List<TenantConfig.IdentityProvider> wanted, UUID existingTenantId,
            Supplier<UUID> tenantId, boolean forApplication, UUID existingAppId, Supplier<UUID> appId) {
        Map<String, Map<String, Object>> current = new LinkedHashMap<>();
        if (existingTenantId != null && (!forApplication || existingAppId != null)) {
            List<Map<String, Object>> rows = forApplication
                    ? providers.listForApplication(existingTenantId, existingAppId)
                    : providers.listPlatform(existingTenantId);
            rows.forEach(r -> current.put((String) r.get("alias"), r));
        }
        Set<String> seen = new HashSet<>();
        for (TenantConfig.IdentityProvider spec : wanted) {
            String alias = spec.alias() == null ? null : spec.alias().trim().toLowerCase(java.util.Locale.ROOT);
            String target = where + ": " + alias;
            if (alias == null || alias.isBlank()) {
                plan.errors.add(where + ": a sign-in provider has no alias");
                continue;
            }
            if (!seen.add(alias)) {
                plan.errors.add(target + ": listed twice");
                continue;
            }
            if (spec.config() != null && spec.config().get("clientSecret") != null) {
                plan.errors.add(target + ": do not put 'clientSecret' in the file; put it in an environment variable on"
                        + " the server and name it with clientSecretEnv");
                continue;
            }
            String secret = plan.secret(spec.clientSecretEnv(), target, null, 1);
            Map<String, Object> existing = current.get(alias);
            Map<String, Object> body = new LinkedHashMap<>();
            if (spec.displayName() != null) body.put("displayName", spec.displayName());
            if (spec.enabled() != null) body.put("enabled", spec.enabled());
            if (spec.config() != null) body.put("config", spec.config());
            if (existing == null) {
                String type = spec.type() == null ? null : spec.type().trim().toUpperCase(java.util.Locale.ROOT);
                if (type == null || !PROVIDER_TYPES.contains(type)) {
                    plan.errors.add(target + ": type must be one of " + String.join(", ", new java.util.TreeSet<>(PROVIDER_TYPES)));
                    continue;
                }
                body.put("type", type);
                body.put("alias", alias);
                if (secret != null) body.put("clientSecret", secret);
                List<String> detail = new ArrayList<>(List.of("type: " + type));
                if (secret != null) detail.add("clientSecret: from " + spec.clientSecretEnv());
                plan.second.add(new Step(new Change("create", "identity-provider", target, detail),
                        () -> providers.create(tenantId.get(), appId.get(), body)));
                continue;
            }
            if (spec.type() != null && !spec.type().equalsIgnoreCase(String.valueOf(existing.get("type")))) {
                plan.errors.add(target + ": the type of an existing provider cannot change (" + existing.get("type")
                        + "); use a new alias");
                continue;
            }
            List<String> diff = new ArrayList<>();
            ConfigPlan.diffText(diff, "displayName", text(existing.get("displayName")), spec.displayName(), false);
            ConfigPlan.diffBoolean(diff, "enabled", Boolean.TRUE.equals(existing.get("enabled")), spec.enabled());
            if (spec.config() != null && !ConfigPlan.contains(existing.get("config"), spec.config())) {
                diff.add("config: updated");
            }
            UUID id = UUID.fromString(existing.get("id").toString());
            if (secret != null && !providers.clientSecretMatches(existingTenantId, id, secret)) {
                body.put("clientSecret", secret);
                diff.add("clientSecret: set from " + spec.clientSecretEnv());
            }
            if (!diff.isEmpty()) {
                plan.second.add(new Step(new Change("update", "identity-provider", target, diff),
                        () -> providers.update(tenantId.get(), id, body)));
            }
        }
        if (plan.prune) {
            current.forEach((alias, row) -> {
                if (!seen.contains(alias)) {
                    UUID id = UUID.fromString(row.get("id").toString());
                    plan.deletions.add(new Step(new Change("delete", "identity-provider", where + ": " + alias,
                                    List.of("users can no longer sign in with it")),
                            () -> providers.delete(tenantId.get(), id)));
                }
            });
        }
    }

    private void planWebhooks(ConfigPlan plan, List<TenantConfig.Webhook> wanted, UUID existingTenantId,
            Supplier<UUID> tenantId, Set<String> appSlugsInFile) {
        Map<String, Map<String, Object>> current = new LinkedHashMap<>();
        Map<UUID, String> slugs = new HashMap<>();
        if (existingTenantId != null) {
            applications.findByTenantIdOrderByCreatedAtDesc(existingTenantId).forEach(a -> slugs.put(a.getId(), a.getSlug()));
            for (Map<String, Object> row : webhooks.list(existingTenantId)) {
                String app = row.get("applicationId") != null ? slugs.get(UUID.fromString(row.get("applicationId").toString())) : null;
                current.put(webhookKey((String) row.get("url"), app), row);
            }
        }
        Set<String> seen = new HashSet<>();
        for (TenantConfig.Webhook spec : wanted) {
            String target = spec.url() + (spec.application() != null ? " (" + spec.application() + ")" : "");
            if (spec.url() == null || spec.url().isBlank()) {
                plan.errors.add("webhooks: a webhook has no url");
                continue;
            }
            String key = webhookKey(spec.url(), spec.application());
            if (!seen.add(key)) {
                plan.errors.add("webhook " + target + ": listed twice");
                continue;
            }
            if (spec.application() != null && !appSlugsInFile.contains(spec.application()) && !slugs.containsValue(spec.application())) {
                plan.errors.add("webhook " + target + ": application '" + spec.application() + "' is neither in the file nor in the tenant");
                continue;
            }
            String secret = plan.secret(spec.secretEnv(), "webhook " + target, null, 32);
            Map<String, Object> existing = current.get(key);
            if (existing == null) {
                List<String> detail = new ArrayList<>();
                if (spec.events() != null) detail.add("events: " + String.join(", ", spec.events()));
                detail.add(secret != null ? "secret: from " + spec.secretEnv() : "secret: generated, shown once");
                plan.second.add(new Step(new Change("create", "webhook", target, detail), () -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("url", spec.url());
                    body.put("applicationId", appIdFor(tenantId.get(), spec.application()));
                    body.put("events", spec.events() != null ? spec.events() : List.of("*"));
                    body.put("enabled", spec.enabled() == null || spec.enabled());
                    Map<String, Object> created = webhooks.create(tenantId.get(), body);
                    UUID id = UUID.fromString(created.get("id").toString());
                    if (secret != null) {
                        webhooks.setSecret(tenantId.get(), id, secret);
                    } else {
                        plan.secrets.add(new CreatedSecret("webhook", spec.url(), String.valueOf(created.get("secret"))));
                    }
                }));
                continue;
            }
            UUID id = UUID.fromString(existing.get("id").toString());
            List<String> diff = new ArrayList<>();
            ConfigPlan.diffSet(diff, "events", strings(existing.get("events")), spec.events());
            ConfigPlan.diffBoolean(diff, "enabled", Boolean.TRUE.equals(existing.get("enabled")), spec.enabled());
            boolean fields = !diff.isEmpty();
            boolean secretChange = secret != null && !webhooks.secretMatches(existingTenantId, id, secret);
            if (secretChange) {
                diff.add("secret: set from " + spec.secretEnv());
            }
            if (!diff.isEmpty()) {
                plan.second.add(new Step(new Change("update", "webhook", target, diff), () -> {
                    if (fields) {
                        Map<String, Object> body = new LinkedHashMap<>();
                        if (spec.events() != null) body.put("events", spec.events());
                        if (spec.enabled() != null) body.put("enabled", spec.enabled());
                        webhooks.update(tenantId.get(), id, body);
                    }
                    if (secretChange) {
                        webhooks.setSecret(tenantId.get(), id, secret);
                    }
                }));
            }
        }
        if (plan.prune) {
            current.forEach((key, row) -> {
                if (!seen.contains(key)) {
                    UUID id = UUID.fromString(row.get("id").toString());
                    plan.deletions.add(new Step(new Change("delete", "webhook", (String) row.get("url"), List.of()),
                            () -> webhooks.delete(tenantId.get(), id)));
                }
            });
        }
    }

    private void planServiceAccounts(ConfigPlan plan, List<TenantConfig.ServiceAccount> wanted, UUID existingTenantId,
            Supplier<UUID> tenantId) {
        Map<String, Map<String, Object>> current = new LinkedHashMap<>();
        if (existingTenantId != null) {
            serviceAccounts.list(existingTenantId).forEach(a -> current.put((String) a.get("name"), a));
        }
        Set<String> seen = new HashSet<>();
        for (TenantConfig.ServiceAccount spec : wanted) {
            String name = spec.name();
            if (name == null || name.isBlank()) {
                plan.errors.add("serviceAccounts: a service account has no name");
                continue;
            }
            if (!seen.add(name)) {
                plan.errors.add("service account '" + name + "': listed twice");
                continue;
            }
            Map<String, Object> existing = current.get(name);
            UUID existingId = existing != null ? UUID.fromString(existing.get("id").toString()) : null;
            Supplier<UUID> accountId = () -> serviceAccounts.list(tenantId.get()).stream()
                    .filter(a -> name.equals(a.get("name")))
                    .map(a -> UUID.fromString(a.get("id").toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Service account not found: " + name));
            if (existing == null) {
                plan.first.add(new Step(new Change("create", "service-account", name, List.of()), () -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", name);
                    if (spec.description() != null) body.put("description", spec.description());
                    if (spec.status() != null) body.put("status", spec.status());
                    serviceAccounts.create(tenantId.get(), body);
                }));
            } else {
                List<String> diff = new ArrayList<>();
                ConfigPlan.diffText(diff, "description", text(existing.get("description")), spec.description(), false);
                ConfigPlan.diffText(diff, "status", text(existing.get("status")), spec.status(), true);
                if (!diff.isEmpty()) {
                    plan.first.add(new Step(new Change("update", "service-account", name, diff), () -> {
                        Map<String, Object> body = new LinkedHashMap<>();
                        if (spec.description() != null) body.put("description", spec.description());
                        if (spec.status() != null) body.put("status", spec.status());
                        serviceAccounts.update(tenantId.get(), existingId, body);
                    }));
                }
            }
            if (spec.apiKeys() != null) {
                planApiKeys(plan, name, spec.apiKeys(), existingTenantId, existingId, tenantId, accountId);
            }
        }
        if (plan.prune) {
            current.forEach((name, row) -> {
                if (!seen.contains(name)) {
                    UUID id = UUID.fromString(row.get("id").toString());
                    plan.deletions.add(new Step(new Change("delete", "service-account", name, List.of("its API keys stop working")),
                            () -> serviceAccounts.delete(tenantId.get(), id)));
                }
            });
        }
    }

    private void planApiKeys(ConfigPlan plan, String account, List<TenantConfig.ApiKey> wanted, UUID existingTenantId,
            UUID existingAccountId, Supplier<UUID> tenantId, Supplier<UUID> accountId) {
        Map<String, List<Map<String, Object>>> activeByLabel = new LinkedHashMap<>();
        if (existingAccountId != null) {
            for (Map<String, Object> key : serviceAccounts.listKeys(existingTenantId, existingAccountId)) {
                if (Boolean.TRUE.equals(key.get("active"))) {
                    activeByLabel.computeIfAbsent(Objects.requireNonNullElse(text(key.get("label")), ""), l -> new ArrayList<>()).add(key);
                }
            }
        }
        Set<String> seen = new HashSet<>();
        for (TenantConfig.ApiKey spec : wanted) {
            String label = spec.label();
            String target = account + ": " + label;
            if (label == null || label.isBlank()) {
                plan.errors.add("service account '" + account + "': an API key has no label");
                continue;
            }
            if (!seen.add(label)) {
                plan.errors.add("API key " + target + ": listed twice");
                continue;
            }
            Instant expiresAt = null;
            if (spec.expiresAt() != null) {
                try {
                    expiresAt = Instant.parse(spec.expiresAt());
                } catch (DateTimeParseException ex) {
                    plan.errors.add("API key " + target + ": expiresAt must be a date-time like 2027-01-31T00:00:00Z");
                    continue;
                }
            }
            Instant expiry = expiresAt;
            String value = plan.secret(spec.keyEnv(), "API key " + target, "sk_live_", 40);
            List<Map<String, Object>> existing = activeByLabel.getOrDefault(label, List.of());
            if (value != null) {
                boolean current = existingAccountId != null && serviceAccounts.hasActiveKey(existingAccountId, value);
                if (!current) {
                    List<UUID> replaced = existing.stream().map(k -> UUID.fromString(k.get("id").toString())).toList();
                    plan.second.add(new Step(new Change(existing.isEmpty() ? "create" : "update", "api-key", target,
                                    List.of("key: set from " + spec.keyEnv() + (replaced.isEmpty() ? "" : " (the current key is revoked)"))),
                            () -> {
                                UUID id = accountId.get();
                                serviceAccounts.importApiKey(tenantId.get(), id, label, value, expiry);
                                replaced.forEach(k -> serviceAccounts.revokeKey(tenantId.get(), id, k));
                            }));
                }
            } else if (spec.keyEnv() == null && existing.isEmpty()) {
                plan.second.add(new Step(new Change("create", "api-key", target, List.of("key: generated, shown once")), () -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("label", label);
                    if (expiry != null) body.put("expiresAt", expiry.toString());
                    Map<String, Object> created = serviceAccounts.createApiKey(tenantId.get(), accountId.get(), body);
                    plan.secrets.add(new CreatedSecret("api-key", target, String.valueOf(created.get("apiKey"))));
                }));
            }
        }
        if (plan.prune) {
            activeByLabel.forEach((label, keys) -> {
                if (!seen.contains(label)) {
                    for (Map<String, Object> key : keys) {
                        UUID keyId = UUID.fromString(key.get("id").toString());
                        plan.deletions.add(new Step(new Change("delete", "api-key", account + ": " + label,
                                        List.of("the key stops working")),
                                () -> serviceAccounts.revokeKey(tenantId.get(), existingAccountId, keyId)));
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- helpers

    private UUID appIdFor(UUID tenantId, String slug) {
        if (slug == null) {
            return null;
        }
        return applications.findByTenantIdAndSlug(tenantId, slug)
                .map(Application::getId)
                .orElseThrow(() -> new IllegalStateException("Application not found: " + slug));
    }

    @SuppressWarnings("unchecked")
    private static TenantConfig.IdentityProvider toProvider(Map<String, Object> row) {
        Map<String, Object> config = new LinkedHashMap<>((Map<String, Object>) row.getOrDefault("config", Map.of()));
        config.remove("clientSecretConfigured");
        config.remove("clientSecretEncrypted");
        return new TenantConfig.IdentityProvider(
                (String) row.get("alias"),
                (String) row.get("type"),
                text(row.get("displayName")),
                Boolean.TRUE.equals(row.get("enabled")),
                config,
                null);
    }

    private static String webhookKey(String url, String application) {
        return url + "\n" + Objects.requireNonNullElse(application, "");
    }

    private static List<String> strings(Object raw) {
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
