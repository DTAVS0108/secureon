package com.secureone.auth.configascode;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationCreateRequest;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationUpdateRequest;
import com.secureone.auth.admin.application.ApplicationAdminService;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientUpdateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminService;
import com.secureone.auth.admin.role.GroupAdminDtos;
import com.secureone.auth.admin.role.GroupAdminService;
import com.secureone.auth.admin.role.PermissionAdminDtos;
import com.secureone.auth.admin.role.PermissionAdminService;
import com.secureone.auth.admin.role.RoleAdminDtos;
import com.secureone.auth.admin.role.RoleAdminService;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantCreateRequest;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantResponse;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantUpdateRequest;
import com.secureone.auth.admin.tenant.TenantAdminService;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSchemaContext;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.SmtpSettingsService;
import com.secureone.auth.notify.sms.ApplicationSmsSettingsService;
import com.secureone.auth.configascode.ConfigPlan.Step;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.rbac.ApplicationRbacScope;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Configuration as code (docs/26): exports a tenant to a {@link TenantConfig}, and compares a file with
 * the database (the dry run) and applies the difference. Every change goes through the same admin
 * services as the console, so validation, audit records and schema provisioning are the same.
 *
 * <p>Applying is not one transaction (each application has its own schema): the whole file is checked
 * first, then the changes run in order. If one fails, the ones before it stay; fix the file and apply it
 * again, and what is already in place shows as unchanged.
 */
@Service
public class TenantConfigService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern PERMISSION_KEY = Pattern.compile("^[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*$");
    /** Application settings sections with credentials, planned by {@link MessagingConfig}. */
    private static final Set<String> MESSAGING_KEYS = Set.of("smtp", "sms");

    public record Change(String action, String kind, String target, List<String> details) {}

    /** A credential SecureOne generated during apply; shown once, never logged. */
    public record CreatedSecret(String kind, String name, String secret) {}

    public record Result(
            boolean dryRun,
            boolean applied,
            boolean prune,
            String tenant,
            List<String> errors,
            List<Change> changes,
            List<CreatedSecret> secrets,
            List<String> notes) {}

    /** An id known only once an earlier step has created the object. */
    private static final class Ref {
        UUID value;

        Ref(UUID value) {
            this.value = value;
        }
    }

    private final TenantRepository tenants;
    private final TenantAdminService tenantAdmin;
    private final ApplicationRepository applications;
    private final ApplicationAdminService applicationAdmin;
    private final OAuthClientRepository clientRepository;
    private final OAuthClientAdminService clientAdmin;
    private final PermissionAdminService permissionAdmin;
    private final RoleAdminService roleAdmin;
    private final GroupAdminService groupAdmin;
    private final ApplicationSettingsService settings;
    private final SmtpSettingsService appSmtp;
    private final ApplicationSmsSettingsService appSms;
    private final TenantIntegrationsConfig integrations;
    private final AuditService audit;
    private Function<String, String> environment = System::getenv;

    public TenantConfigService(
            TenantRepository tenants,
            TenantAdminService tenantAdmin,
            ApplicationRepository applications,
            ApplicationAdminService applicationAdmin,
            OAuthClientRepository clientRepository,
            OAuthClientAdminService clientAdmin,
            PermissionAdminService permissionAdmin,
            RoleAdminService roleAdmin,
            GroupAdminService groupAdmin,
            ApplicationSettingsService settings,
            SmtpSettingsService appSmtp,
            ApplicationSmsSettingsService appSms,
            TenantIntegrationsConfig integrations,
            AuditService audit) {
        this.tenants = tenants;
        this.tenantAdmin = tenantAdmin;
        this.applications = applications;
        this.applicationAdmin = applicationAdmin;
        this.clientRepository = clientRepository;
        this.clientAdmin = clientAdmin;
        this.permissionAdmin = permissionAdmin;
        this.roleAdmin = roleAdmin;
        this.groupAdmin = groupAdmin;
        this.settings = settings;
        this.appSmtp = appSmtp;
        this.appSms = appSms;
        this.integrations = integrations;
        this.audit = audit;
    }

    void setEnvironment(Function<String, String> environment) {
        this.environment = environment;
    }

    // ---------------------------------------------------------------- export

    public TenantConfig export(String tenantSlug) {
        return RlsScope.asPlatform(() -> {
            Tenant tenant = tenants.findBySlug(tenantSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("No tenant with slug '" + tenantSlug + "'"));
            TenantResponse info = tenantAdmin.get(tenant.getId());
            List<TenantConfig.Application> apps = new ArrayList<>();
            List<Application> list = new ArrayList<>(applications.findByTenantIdOrderByCreatedAtDesc(tenant.getId()));
            list.sort(Comparator.comparing(Application::getSlug));
            Map<UUID, String> appSlugs = new HashMap<>();
            for (Application app : list) {
                appSlugs.put(app.getId(), app.getSlug());
                apps.add(inApp(app.getId(), () -> exportApplication(tenant.getId(), app)));
            }
            return new TenantConfig(
                    TenantConfig.API_VERSION,
                    TenantConfig.KIND,
                    null,
                    new TenantConfig.Tenant(info.slug(), info.name(), info.plan(), info.status()),
                    integrations.exportScim(tenant.getId()),
                    integrations.exportProviders(tenant.getId(), null),
                    integrations.exportWebhooks(tenant.getId(), appSlugs),
                    integrations.exportServiceAccounts(tenant.getId()),
                    apps);
        });
    }

    private TenantConfig.Application exportApplication(UUID tenantId, Application app) {
        List<TenantConfig.Client> clients = clientAdmin.list(tenantId, app.getId()).stream()
                .sorted(Comparator.comparing(OAuthClientResponse::clientId))
                .map(c -> new TenantConfig.Client(
                        c.clientId(), c.type(), c.status(), sorted(c.grantTypes()), sorted(c.scopes()),
                        sorted(c.redirectUris()), sorted(c.postLogoutRedirectUris()), c.pkceRequired(),
                        c.tokenEndpointAuthMethod(), c.subjectType(), c.backchannelLogoutUri(),
                        c.consentRequired() ? Boolean.TRUE : null, null, c.jwksUri(), c.jwks()))
                .toList();
        List<TenantConfig.Permission> permissions = permissionAdmin.list(app.getId()).stream()
                .sorted(Comparator.comparing(RoleAdminDtos.PermissionResponse::key))
                .map(p -> new TenantConfig.Permission(p.key(), blankToNull(p.description())))
                .toList();
        List<TenantConfig.Role> roles = new ArrayList<>();
        for (RoleState role : roleStates(tenantId, app.getId()).values()) {
            roles.add(new TenantConfig.Role(
                    role.name(),
                    blankToNull(role.description()),
                    role.composite() ? Boolean.TRUE : null,
                    sorted(role.permissionKeys()),
                    role.composite() ? sorted(role.includes()) : null));
        }
        roles.sort(Comparator.comparing(TenantConfig.Role::name));
        List<TenantConfig.Group> groups = new ArrayList<>();
        for (GroupAdminDtos.GroupSummaryResponse summary : groupAdmin.listGroups(app.getId())) {
            GroupAdminDtos.GroupDetailResponse group = groupAdmin.getGroup(summary.id(), app.getId());
            groups.add(new TenantConfig.Group(
                    group.name(),
                    blankToNull(group.description()),
                    sorted(group.roles().stream().map(RoleAdminDtos.RoleRefResponse::name).toList())));
        }
        groups.sort(Comparator.comparing(TenantConfig.Group::name));
        Map<String, Object> overrides = new LinkedHashMap<>(settings.exportOverrides(app.getId()));
        if (appSmtp.hasOverride(app.getId())) {
            Map<String, Object> smtp = MessagingConfig.toMap(MessagingConfig.smtpFromView(appSmtp.getPublicSettings(app.getId())));
            if (smtp != null) {
                overrides.put("smtp", smtp);
            }
        }
        if (appSms.hasOverride(app.getId())) {
            overrides.put("sms", MessagingConfig.toMap(MessagingConfig.smsFromView(appSms.getPublicSettings(app.getId()))));
        }
        return new TenantConfig.Application(
                app.getSlug(), app.getName(), blankToNull(app.getDescription()), app.getStatus(),
                clients, permissions, roles, groups, integrations.exportProviders(tenantId, app.getId()), overrides);
    }

    // ---------------------------------------------------------------- plan / apply

    /** The dry run: what applying the file would change, and every problem that would stop it. */
    public Result plan(TenantConfig config, boolean prune) {
        return RlsScope.asPlatform(() -> {
            Run run = compute(config, prune);
            return run.result(true, false, run.changes());
        });
    }

    public Result apply(TenantConfig config, boolean prune, String actor) {
        return RlsScope.asPlatform(() -> {
            Run run = compute(config, prune);
            if (!run.errors.isEmpty()) {
                return run.result(false, false, run.changes());
            }
            List<Change> done = run.execute();
            UUID tenantId = run.tenant.value;
            if (tenantId != null && !done.isEmpty()) {
                audit.record(tenantId, actor, "config.imported", "tenant", tenantId,
                        config.tenant().slug() + ": " + done.size() + " change(s)" + (run.errors.isEmpty() ? "" : ", stopped early"),
                        run.errors.isEmpty());
            }
            return run.result(false, run.errors.isEmpty(), done);
        });
    }

    private Run compute(TenantConfig config, boolean prune) {
        Run run = new Run(config, prune || Boolean.TRUE.equals(config.prune()), environment);
        run.validateShape();
        if (run.errors.isEmpty()) {
            run.planTenant();
        }
        return run;
    }

    /** One plan: the checks, the changes in the order they run, and what to do for each. */
    private final class Run extends ConfigPlan {
        final TenantConfig config;
        final Ref tenant = new Ref(null);
        final Set<String> clientIdsInFile = new HashSet<>();

        Run(TenantConfig config, boolean prune, Function<String, String> environment) {
            super(prune, environment);
            this.config = config;
        }

        Result result(boolean dryRun, boolean applied, List<Change> changes) {
            String slug = config.tenant() != null ? config.tenant().slug() : null;
            return new Result(dryRun, applied, prune, slug, List.copyOf(errors), List.copyOf(changes),
                    List.copyOf(secrets), List.copyOf(notes));
        }

        // ------------------------------------------------------------ checks that need no database

        void validateShape() {
            if (!TenantConfig.API_VERSION.equals(config.apiVersion())) {
                errors.add("apiVersion must be '" + TenantConfig.API_VERSION + "'");
            }
            if (!TenantConfig.KIND.equals(config.kind())) {
                errors.add("kind must be '" + TenantConfig.KIND + "'");
            }
            if (config.tenant() == null || isBlank(config.tenant().slug())) {
                errors.add("tenant.slug is required");
            } else if (!SLUG.matcher(config.tenant().slug()).matches()) {
                errors.add("tenant.slug '" + config.tenant().slug() + "' must be lowercase letters, digits and dashes");
            }
            Set<String> appSlugs = new HashSet<>();
            for (TenantConfig.Application app : nonNull(config.applications())) {
                String where = "applications[" + (app.slug() != null ? app.slug() : "?") + "]";
                if (isBlank(app.slug()) || !SLUG.matcher(app.slug()).matches()) {
                    errors.add(where + ": slug is required (lowercase letters, digits and dashes)");
                    continue;
                }
                if (!appSlugs.add(app.slug())) {
                    errors.add(where + ": listed twice");
                }
                for (TenantConfig.Client client : nonNull(app.clients())) {
                    if (isBlank(client.clientId())) {
                        errors.add(where + ": a client has no clientId");
                    } else if (!clientIdsInFile.add(client.clientId())) {
                        errors.add(where + ": client '" + client.clientId() + "' is listed twice");
                    }
                }
                duplicates(where, "permission", nonNull(app.permissions()).stream().map(TenantConfig.Permission::key).toList());
                for (TenantConfig.Permission p : nonNull(app.permissions())) {
                    if (p.key() == null || !PERMISSION_KEY.matcher(p.key()).matches()) {
                        errors.add(where + ": permission key '" + p.key() + "' must be resource:action (lowercase, e.g. orders:read)");
                    }
                }
                duplicates(where, "role", nonNull(app.roles()).stream().map(TenantConfig.Role::name).toList());
                duplicates(where, "group", nonNull(app.groups()).stream().map(TenantConfig.Group::name).toList());
                if (app.settings() != null) {
                    app.settings().forEach((key, value) -> {
                        if (MESSAGING_KEYS.contains(key)) {
                            return; // checked in detail while planning
                        }
                        if (!ApplicationSettingsService.PORTABLE_SETTING_KEYS.contains(key)) {
                            errors.add(where + ": unknown settings section '" + key + "'. Allowed: "
                                    + String.join(", ", ApplicationSettingsService.PORTABLE_SETTING_KEYS) + ", smtp, sms");
                        } else if (ApplicationSettingsService.isListSetting(key) ? !(value instanceof List<?>) : !(value instanceof Map<?, ?>)) {
                            errors.add(where + ": settings." + key + " must be "
                                    + (ApplicationSettingsService.isListSetting(key) ? "a list" : "a mapping (key: value)"));
                        }
                    });
                }
            }
        }

        void duplicates(String where, String what, List<String> names) {
            Set<String> seen = new HashSet<>();
            for (String name : names) {
                if (isBlank(name)) {
                    errors.add(where + ": a " + what + " has no name");
                } else if (!seen.add(name)) {
                    errors.add(where + ": " + what + " '" + name + "' is listed twice");
                }
            }
        }

        // ------------------------------------------------------------ tenant and applications

        void planTenant() {
            TenantConfig.Tenant spec = config.tenant();
            Tenant existing = tenants.findBySlug(spec.slug()).orElse(null);
            if (existing == null) {
                if (isBlank(spec.name())) {
                    errors.add("tenant.name is required to create tenant '" + spec.slug() + "'");
                }
                first.add(new Step(
                        new Change("create", "tenant", spec.slug(), describe(Map.of(
                                "name", String.valueOf(spec.name()), "plan", orDefault(spec.plan(), "free"),
                                "status", orDefault(spec.status(), "ACTIVE")))),
                        () -> tenant.value = tenantAdmin.create(
                                new TenantCreateRequest(spec.name(), spec.slug(), spec.plan(), spec.status())).id()));
            } else {
                tenant.value = existing.getId();
                TenantResponse current = tenantAdmin.get(existing.getId());
                List<String> diff = new ArrayList<>();
                diffText(diff, "name", current.name(), spec.name(), false);
                diffText(diff, "plan", current.plan(), spec.plan(), true);
                diffText(diff, "status", current.status(), spec.status(), true);
                if (!diff.isEmpty()) {
                    first.add(new Step(new Change("update", "tenant", spec.slug(), diff),
                            () -> tenantAdmin.update(existing.getId(), new TenantUpdateRequest(
                                    orDefault(spec.name(), current.name()), current.slug(),
                                    orDefault(spec.plan(), current.plan()), orDefault(spec.status(), current.status())))));
                }
            }
            UUID existingTenantId = tenant.value;
            for (TenantConfig.Application app : nonNull(config.applications())) {
                Application existingApp = tenant.value == null
                        ? null
                        : applications.findByTenantIdAndSlug(tenant.value, app.slug()).orElse(null);
                planApplication(app, existingApp);
            }
            integrations.planTenant(this, config, existingTenantId, () -> tenant.value,
                    new HashSet<>(nonNull(config.applications()).stream().map(TenantConfig.Application::slug).toList()));
            if (config.applications() != null && tenant.value != null) {
                Set<String> listed = new HashSet<>(config.applications().stream().map(TenantConfig.Application::slug).toList());
                applications.findByTenantIdOrderByCreatedAtDesc(tenant.value).stream()
                        .filter(a -> !listed.contains(a.getSlug()))
                        .forEach(a -> notes.add("Application '" + a.getSlug()
                                + "' is not in the file and is left as it is (applications are never deleted by an import)."));
            }
        }

        void planApplication(TenantConfig.Application spec, Application existing) {
            String target = spec.slug();
            Ref app = new Ref(existing != null ? existing.getId() : null);
            if (existing == null) {
                if (isBlank(spec.name())) {
                    errors.add("applications[" + target + "]: name is required to create the application");
                }
                first.add(new Step(
                        new Change("create", "application", target, describe(Map.of("name", String.valueOf(spec.name())))),
                        () -> app.value = applicationAdmin.create(new ApplicationCreateRequest(
                                tenant.value, spec.name(), spec.description(), spec.slug(), spec.status())).id()));
            } else {
                List<String> diff = new ArrayList<>();
                diffText(diff, "name", existing.getName(), spec.name(), false);
                diffText(diff, "description", blankToNull(existing.getDescription()), spec.description(), false);
                diffText(diff, "status", existing.getStatus(), spec.status(), true);
                if (!diff.isEmpty()) {
                    first.add(new Step(new Change("update", "application", target, diff),
                            () -> applicationAdmin.update(existing.getId(), new ApplicationUpdateRequest(
                                    orDefault(spec.name(), existing.getName()),
                                    spec.description() != null ? spec.description() : existing.getDescription(),
                                    orDefault(spec.status(), existing.getStatus())))));
                }
            }
            UUID tenantId = tenant.value;
            if (existing != null) {
                inApp(existing.getId(), () -> {
                    planApplicationContents(spec, app, tenantId, existing.getId());
                    return null;
                });
            } else {
                planApplicationContents(spec, app, tenantId, null);
            }
        }

        // ------------------------------------------------------------ contents of one application

        void planApplicationContents(TenantConfig.Application spec, Ref app, UUID tenantId, UUID existingAppId) {
            String slug = spec.slug();
            planClients(spec, app, tenantId, existingAppId, slug);

            Map<String, RoleAdminDtos.PermissionResponse> currentPermissions = new LinkedHashMap<>();
            Map<String, RoleState> currentRoles = new LinkedHashMap<>();
            Map<String, String> heldByConsoleRoles = new HashMap<>();
            Map<String, GroupAdminDtos.GroupDetailResponse> currentGroups = new LinkedHashMap<>();
            Map<String, Object> currentSettings = Map.of();
            if (existingAppId != null) {
                permissionAdmin.list(existingAppId).forEach(p -> currentPermissions.put(p.key(), p));
                currentRoles.putAll(roleStates(tenantId, existingAppId));
                heldByConsoleRoles.putAll(permissionsOfConsoleRoles(tenantId, existingAppId));
                for (GroupAdminDtos.GroupSummaryResponse g : groupAdmin.listGroups(existingAppId)) {
                    currentGroups.put(g.name(), groupAdmin.getGroup(g.id(), existingAppId));
                }
                currentSettings = settings.exportOverrides(existingAppId);
            }

            // Permissions
            Set<String> knownPermissions = new HashSet<>(currentPermissions.keySet());
            if (spec.permissions() != null) {
                Set<String> wanted = new HashSet<>();
                for (TenantConfig.Permission p : spec.permissions()) {
                    wanted.add(p.key());
                    RoleAdminDtos.PermissionResponse current = currentPermissions.get(p.key());
                    if (current == null) {
                        first.add(new Step(new Change("create", "permission", slug + ": " + p.key(), List.of()),
                                inApp(app, () -> upsertPermission(app.value, p))));
                    } else if (p.description() != null && !p.description().equals(blankToNull(current.description()))) {
                        first.add(new Step(new Change("update", "permission", slug + ": " + p.key(),
                                        List.of("description: " + quote(current.description()) + " → " + quote(p.description()))),
                                inApp(app, () -> upsertPermission(app.value, p))));
                    }
                }
                if (prune) {
                    // Roles that keep the permissions they have now: roles the file leaves out but that stay
                    // (built-in, or held by users), and listed roles without a permissions list.
                    Map<String, String> stillUsed = new HashMap<>(heldByConsoleRoles);
                    for (RoleState role : currentRoles.values()) {
                        TenantConfig.Role listed = nonNull(spec.roles()).stream()
                                .filter(r -> role.name().equals(r.name())).findFirst().orElse(null);
                        boolean keepsPermissions = listed != null
                                ? listed.permissions() == null
                                : spec.roles() == null || role.system() || role.users() > 0;
                        if (keepsPermissions) {
                            role.permissionKeys().forEach(k -> stillUsed.putIfAbsent(k, role.name()));
                        }
                    }
                    knownPermissions.retainAll(wanted);
                    for (RoleAdminDtos.PermissionResponse p : currentPermissions.values()) {
                        if (wanted.contains(p.key())) {
                            continue;
                        }
                        if (stillUsed.containsKey(p.key())) {
                            knownPermissions.add(p.key());
                            notes.add("Permission '" + p.key() + "' of " + slug + " is not in the file but is kept: role '"
                                    + stillUsed.get(p.key()) + "' still has it.");
                            continue;
                        }
                        deletions.add(new Step(new Change("delete", "permission", slug + ": " + p.key(), List.of()),
                                inApp(app, () -> permissionAdmin.delete(app.value, p.id()))));
                    }
                }
                knownPermissions.addAll(wanted);
            }

            // Roles
            Set<String> knownRoles = new HashSet<>(currentRoles.keySet());
            if (spec.roles() != null) {
                Set<String> wanted = new HashSet<>(spec.roles().stream().map(TenantConfig.Role::name).toList());
                if (prune) {
                    for (RoleState role : currentRoles.values()) {
                        if (wanted.contains(role.name())) {
                            continue;
                        }
                        if (role.system()) {
                            notes.add("Built-in role '" + role.name() + "' of " + slug + " is not in the file and is kept.");
                            continue;
                        }
                        long users = role.users();
                        if (users > 0) {
                            errors.add(slug + ": role '" + role.name() + "' is not in the file but " + users
                                    + " user(s) have it; keep it in the file or remove the assignments first");
                            continue;
                        }
                        knownRoles.remove(role.name());
                        deletions.add(new Step(new Change("delete", "role", slug + ": " + role.name(), List.of()),
                                inApp(app, () -> roleAdmin.delete(role.id(), app.value))));
                    }
                }
                knownRoles.addAll(wanted);
                for (TenantConfig.Role role : spec.roles()) {
                    planRole(slug, app, tenantId, role, currentRoles.get(role.name()), knownPermissions, knownRoles);
                }
            }

            // Groups
            if (spec.groups() != null) {
                Set<String> wanted = new HashSet<>();
                for (TenantConfig.Group group : spec.groups()) {
                    wanted.add(group.name());
                    planGroup(slug, app, tenantId, group, currentGroups.get(group.name()), knownRoles);
                }
                if (prune) {
                    currentGroups.values().stream().filter(g -> !wanted.contains(g.name())).forEach(g -> {
                        List<String> detail = g.memberCount() > 0 ? List.of(g.memberCount() + " member(s) lose this group") : List.of();
                        deletions.add(new Step(new Change("delete", "group", slug + ": " + g.name(), detail),
                                inApp(app, () -> groupAdmin.delete(g.id(), app.value))));
                    });
                }
            }

            // Sign-in providers of this application
            if (spec.identityProviders() != null) {
                integrations.planProviders(this, slug, spec.identityProviders(), tenantId, () -> tenant.value, true,
                        existingAppId, () -> app.value);
            }

            // Settings
            if (spec.settings() != null) {
                planMessaging(spec, slug, app, existingAppId);
                for (Map.Entry<String, Object> entry : spec.settings().entrySet()) {
                    if (MESSAGING_KEYS.contains(entry.getKey())) {
                        continue;
                    }
                    Object current = currentSettings.get(entry.getKey());
                    Object wanted = ApplicationSettingsService.redactCredentials(entry.getValue());
                    if (current == null || !contains(current, wanted)) {
                        second.add(new Step(
                                new Change(current == null ? "create" : "update", "settings", slug + ": " + entry.getKey(),
                                        current == null ? List.of("override the platform default") : List.of("replace the override")),
                                inApp(app, () -> settings.importOverride(app.value, entry.getKey(), entry.getValue()))));
                    }
                }
                if (prune) {
                    Set<String> overridden = new HashSet<>(currentSettings.keySet());
                    if (existingAppId != null && appSmtp.hasOverride(existingAppId)) {
                        overridden.add("smtp");
                    }
                    if (existingAppId != null && appSms.hasOverride(existingAppId)) {
                        overridden.add("sms");
                    }
                    for (String key : overridden) {
                        if (!spec.settings().containsKey(key)) {
                            deletions.add(new Step(
                                    new Change("delete", "settings", slug + ": " + key, List.of("use the platform default again")),
                                    inApp(app, () -> settings.clearOverride(app.value, key))));
                        }
                    }
                }
            }
        }

        /** settings.smtp and settings.sms: the application's own mail server and SMS provider. */
        void planMessaging(TenantConfig.Application spec, String slug, Ref app, UUID existingAppId) {
            Object smtpRaw = spec.settings().get("smtp");
            if (smtpRaw != null) {
                TenantConfig.Smtp smtp = MessagingConfig.smtp(this, slug + ": settings.smtp", smtpRaw);
                if (smtp != null) {
                    Map<String, Object> current = existingAppId != null && appSmtp.hasOverride(existingAppId)
                            ? appSmtp.getPublicSettings(existingAppId) : null;
                    MessagingConfig.planSmtp(this, slug, smtp, current,
                            password -> appSmtp.passwordMatches(existingAppId, password),
                            body -> inApp(app.value, () -> settings.saveSmtp(app.value, body)));
                }
            }
            Object smsRaw = spec.settings().get("sms");
            if (smsRaw != null) {
                TenantConfig.Sms sms = MessagingConfig.sms(this, slug + ": settings.sms", smsRaw);
                if (sms != null) {
                    Map<String, Object> current = existingAppId != null && appSms.hasOverride(existingAppId)
                            ? appSms.getPublicSettings(existingAppId) : null;
                    MessagingConfig.planSms(this, slug, sms, current,
                            () -> appSms.resolveCredentials(existingAppId),
                            body -> inApp(app.value, () -> settings.saveSms(app.value, body)));
                }
            }
        }

        void planClients(TenantConfig.Application spec, Ref app, UUID tenantId, UUID existingAppId, String slug) {
            if (spec.clients() == null) {
                return;
            }
            Map<String, OAuthClientResponse> current = new LinkedHashMap<>();
            if (existingAppId != null) {
                clientAdmin.list(tenantId, existingAppId).forEach(c -> current.put(c.clientId(), c));
            }
            for (TenantConfig.Client spec2 : spec.clients()) {
                String target = spec2.clientId();
                OAuthClientResponse existing = current.get(target);
                if (existing == null) {
                    clientRepository.findByClientId(target).ifPresent(other -> errors.add(
                            "client '" + target + "' already exists in another application; client IDs are unique across SecureOne"));
                }
                String secret = null;
                if (spec2.clientSecretEnv() != null) {
                    String type = (spec2.type() != null ? spec2.type() : existing != null ? existing.type() : "web").toLowerCase(Locale.ROOT);
                    secret = environment.apply(spec2.clientSecretEnv());
                    if (!"web".equals(type) && !"m2m".equals(type)) {
                        errors.add("client '" + target + "': clientSecretEnv is only for confidential clients (type web or m2m)");
                    } else if (isBlank(secret)) {
                        errors.add("client '" + target + "': environment variable " + spec2.clientSecretEnv()
                                + " (clientSecretEnv) is not set on the server");
                    } else if (secret.length() < 32) {
                        errors.add("client '" + target + "': the secret in " + spec2.clientSecretEnv() + " must be at least 32 characters");
                    }
                }
                String secretValue = secret;
                if (existing == null) {
                    List<String> detail = new ArrayList<>();
                    detail.add("type: " + orDefault(spec2.type(), "web"));
                    if (spec2.redirectUris() != null && !spec2.redirectUris().isEmpty()) {
                        detail.add("redirectUris: " + spec2.redirectUris());
                    }
                    detail.add(spec2.clientSecretEnv() != null
                            ? "secret: from " + spec2.clientSecretEnv()
                            : "secret: generated if the client is confidential");
                    first.add(new Step(new Change("create", "oauth-client", target, detail), inApp(app, () -> {
                        var created = clientAdmin.create(new OAuthClientCreateRequest(
                                app.value, null, tenant.value, target, spec2.type(), spec2.status(), spec2.grantTypes(),
                                spec2.scopes(), spec2.redirectUris(), spec2.postLogoutRedirectUris(), spec2.pkceRequired(),
                                spec2.tokenEndpointAuthMethod(), spec2.subjectType(), null, spec2.backchannelLogoutUri(),
                                spec2.consentRequired(), spec2.jwksUri(), spec2.jwks()));
                        if (secretValue != null) {
                            clientAdmin.setClientSecret(created.client().id(), secretValue);
                        } else if (created.clientSecret() != null) {
                            secrets.add(new CreatedSecret("oauth-client", target, created.clientSecret()));
                        }
                    })));
                } else {
                    List<String> diff = new ArrayList<>();
                    diffText(diff, "type", existing.type(), spec2.type(), true);
                    diffText(diff, "status", existing.status(), spec2.status(), true);
                    diffSet(diff, "grantTypes", existing.grantTypes(), spec2.grantTypes());
                    diffSet(diff, "scopes", existing.scopes(), spec2.scopes());
                    diffSet(diff, "redirectUris", existing.redirectUris(), spec2.redirectUris());
                    diffSet(diff, "postLogoutRedirectUris", existing.postLogoutRedirectUris(), spec2.postLogoutRedirectUris());
                    if (spec2.pkceRequired() != null && spec2.pkceRequired() != existing.pkceRequired()) {
                        diff.add("pkceRequired: " + existing.pkceRequired() + " → " + spec2.pkceRequired());
                    }
                    diffText(diff, "tokenEndpointAuthMethod", existing.tokenEndpointAuthMethod(), spec2.tokenEndpointAuthMethod(), true);
                    diffText(diff, "subjectType", existing.subjectType(), spec2.subjectType(), true);
                    diffText(diff, "backchannelLogoutUri", existing.backchannelLogoutUri(), spec2.backchannelLogoutUri(), true);
                    if (spec2.consentRequired() != null && spec2.consentRequired() != existing.consentRequired()) {
                        diff.add("consentRequired: " + existing.consentRequired() + " → " + spec2.consentRequired());
                    }
                    diffText(diff, "jwksUri", existing.jwksUri(), spec2.jwksUri(), true);
                    if (spec2.jwks() != null && !java.util.Objects.equals(existing.jwks(), spec2.jwks())) {
                        diff.add("jwks: replaced");
                    }
                    boolean fieldsChanged = !diff.isEmpty();
                    boolean secretChanged = secretValue != null && secretValue.length() >= 32
                            && !clientAdmin.clientSecretMatches(existing.id(), secretValue);
                    if (secretChanged) {
                        diff.add("secret: set from " + spec2.clientSecretEnv());
                    }
                    if (!diff.isEmpty()) {
                        first.add(new Step(new Change("update", "oauth-client", target, diff), inApp(app, () -> {
                            if (fieldsChanged) {
                                clientAdmin.update(existing.id(), new OAuthClientUpdateRequest(
                                        spec2.type(), spec2.status(), spec2.grantTypes(), spec2.scopes(), spec2.redirectUris(),
                                        spec2.postLogoutRedirectUris(), spec2.pkceRequired(), spec2.tokenEndpointAuthMethod(),
                                        spec2.subjectType(), null, spec2.backchannelLogoutUri(), spec2.consentRequired(),
                                        spec2.jwksUri(), spec2.jwks()));
                            }
                            if (secretChanged) {
                                clientAdmin.setClientSecret(existing.id(), secretValue);
                            }
                        })));
                    }
                }
            }
            if (prune) {
                Set<String> wanted = new HashSet<>(spec.clients().stream().map(TenantConfig.Client::clientId).toList());
                current.values().stream().filter(c -> !wanted.contains(c.clientId())).forEach(c ->
                        deletions.add(new Step(new Change("delete", "oauth-client", c.clientId(),
                                        List.of("apps using it can no longer sign in")),
                                inApp(app, () -> clientAdmin.delete(c.id())))));
            }
        }

        void planRole(String slug, Ref app, UUID tenantId, TenantConfig.Role spec, RoleState current,
                Set<String> knownPermissions, Set<String> knownRoles) {
            String target = slug + ": " + spec.name();
            if (!ApplicationRbacScope.isApplicationScopedRoleName(spec.name())) {
                errors.add(target + ": this role belongs to console operator access and is managed there, not in files");
                return;
            }
            for (String key : nonNull(spec.permissions())) {
                if (!knownPermissions.contains(key)) {
                    errors.add(target + ": permission '" + key + "' is neither in the file nor in the application");
                }
            }
            for (String name : nonNull(spec.includes())) {
                if (name.equals(spec.name())) {
                    errors.add(target + ": a role cannot include itself");
                } else if (!knownRoles.contains(name)) {
                    errors.add(target + ": included role '" + name + "' is neither in the file nor in the application");
                }
            }
            boolean composite = spec.composite() != null
                    ? spec.composite()
                    : spec.includes() != null ? !spec.includes().isEmpty() : current != null && current.composite();
            if (!composite && !nonNull(spec.includes()).isEmpty()) {
                errors.add(target + ": composite: false, but the role includes other roles");
            }
            List<String> diff = new ArrayList<>();
            if (current == null) {
                if (!nonNull(spec.permissions()).isEmpty()) {
                    diff.add("permissions: " + String.join(", ", spec.permissions()));
                }
                if (!nonNull(spec.includes()).isEmpty()) {
                    diff.add("includes: " + String.join(", ", spec.includes()));
                }
            } else {
                diffText(diff, "description", blankToNull(current.description()), spec.description(), false);
                if (composite != current.composite()) {
                    diff.add("composite: " + current.composite() + " → " + composite);
                }
                diffSet(diff, "permissions", current.permissionKeys(), spec.permissions());
                if (composite) {
                    diffSet(diff, "includes", current.includes(), spec.includes());
                }
            }
            if (current != null && diff.isEmpty()) {
                return;
            }
            Change change = new Change(current == null ? "create" : "update", "role", target, diff);
            // Roles first (with their permissions), then the includes, once every role exists. Both steps
            // share one change, so it is listed once.
            first.add(new Step(change, inApp(app, () -> upsertRole(app.value, tenantId != null ? tenantId : tenant.value, spec, composite))));
            boolean includesChange = diff.stream().anyMatch(d -> d.startsWith("includes:"));
            if (composite && spec.includes() != null && includesChange) {
                second.add(new Step(change, inApp(app, () -> setIncludes(app.value, spec))));
            }
        }

        void planGroup(String slug, Ref app, UUID tenantId, TenantConfig.Group spec, GroupAdminDtos.GroupDetailResponse current,
                Set<String> knownRoles) {
            String target = slug + ": " + spec.name();
            for (String name : nonNull(spec.roles())) {
                if (!knownRoles.contains(name)) {
                    errors.add(target + ": role '" + name + "' is neither in the file nor in the application");
                }
            }
            List<String> diff = new ArrayList<>();
            if (current == null) {
                if (!nonNull(spec.roles()).isEmpty()) {
                    diff.add("roles: " + String.join(", ", spec.roles()));
                }
            } else {
                diffText(diff, "description", blankToNull(current.description()), spec.description(), false);
                diffSet(diff, "roles", current.roles().stream().map(RoleAdminDtos.RoleRefResponse::name).toList(), spec.roles());
                if (diff.isEmpty()) {
                    return;
                }
            }
            second.add(new Step(new Change(current == null ? "create" : "update", "group", target, diff),
                    inApp(app, () -> upsertGroup(app.value, tenantId != null ? tenantId : tenant.value, spec))));
        }
    }

    // ---------------------------------------------------------------- the changes themselves (run at apply time)

    private void upsertPermission(UUID appId, TenantConfig.Permission spec) {
        var existing = permissionAdmin.list(appId).stream().filter(p -> p.key().equals(spec.key())).findFirst();
        if (existing.isEmpty()) {
            permissionAdmin.create(appId, new PermissionAdminDtos.PermissionCreateRequest(spec.key(), spec.description()));
        } else if (spec.description() != null) {
            permissionAdmin.update(appId, existing.get().id(), new PermissionAdminDtos.PermissionUpdateRequest(spec.description(), spec.key()));
        }
    }

    private void upsertRole(UUID appId, UUID tenantId, TenantConfig.Role spec, boolean composite) {
        Map<String, UUID> permissionIds = new HashMap<>();
        permissionAdmin.list(appId).forEach(p -> permissionIds.put(p.key(), p.id()));
        RoleAdminDtos.RoleDetailResponse existing = findRole(tenantId, appId, spec.name());
        List<UUID> permissions = new ArrayList<>();
        if (spec.permissions() != null) {
            spec.permissions().stream().map(permissionIds::get).filter(Objects::nonNull).forEach(permissions::add);
            if (existing != null) {
                // Console operator permissions are not in files; keep the ones the role has.
                existing.permissionIds().stream().filter(id -> !permissionIds.containsValue(id)).forEach(permissions::add);
            }
        } else if (existing != null) {
            permissions.addAll(existing.permissionIds());
        }
        if (existing == null) {
            roleAdmin.create(new RoleAdminDtos.RoleCreateRequest(
                    tenantId, appId, spec.name(), spec.description(), composite, permissions, List.of()));
        } else {
            // Includes are set in the second pass; keep the current ones until then.
            List<UUID> children = composite ? existing.childRoleIds() : List.of();
            roleAdmin.update(existing.id(), appId, new RoleAdminDtos.RoleUpdateRequest(
                    spec.name(), spec.description() != null ? spec.description() : existing.description(),
                    composite, permissions, children));
        }
    }

    private void setIncludes(UUID appId, TenantConfig.Role spec) {
        UUID tenantId = applications.findById(appId).map(Application::getTenantId).orElseThrow();
        Map<String, UUID> roleIds = new HashMap<>();
        roleAdmin.listRoles(tenantId, appId).forEach(r -> roleIds.put(r.name(), r.id()));
        RoleAdminDtos.RoleDetailResponse role = findRole(tenantId, appId, spec.name());
        if (role == null) {
            throw new ResourceNotFoundException("Role not found: " + spec.name());
        }
        roleAdmin.update(role.id(), appId, new RoleAdminDtos.RoleUpdateRequest(
                role.name(), role.description(), true, role.permissionIds(),
                spec.includes().stream().map(roleIds::get).filter(Objects::nonNull).toList()));
    }

    private void upsertGroup(UUID appId, UUID tenantId, TenantConfig.Group spec) {
        Map<String, UUID> roleIds = new HashMap<>();
        roleAdmin.listRoles(tenantId, appId).forEach(r -> roleIds.put(r.name(), r.id()));
        var existing = groupAdmin.listGroups(appId).stream().filter(g -> g.name().equals(spec.name())).findFirst();
        if (existing.isEmpty()) {
            groupAdmin.create(new GroupAdminDtos.GroupCreateRequest(
                    tenantId, appId, spec.name(), spec.description(),
                    nonNull(spec.roles()).stream().map(roleIds::get).filter(Objects::nonNull).toList(), List.of()));
        } else {
            GroupAdminDtos.GroupDetailResponse current = groupAdmin.getGroup(existing.get().id(), appId);
            List<UUID> roles = spec.roles() != null
                    ? spec.roles().stream().map(roleIds::get).filter(Objects::nonNull).toList()
                    : current.roleIds();
            // Members are people, not configuration: they stay as they are.
            groupAdmin.update(current.id(), appId, new GroupAdminDtos.GroupUpdateRequest(
                    spec.name(), spec.description() != null ? spec.description() : current.description(),
                    roles, current.memberUserIds()));
        }
    }

    /** A role as files see it: application permissions by key, included roles by name. */
    private record RoleState(
            UUID id, String name, String description, boolean composite, boolean system, long users,
            List<String> permissionKeys, List<String> includes) {}

    /**
     * The application's roles, without the console operator roles (managed by console access). The role
     * detail's {@code permissions} is the app's catalog for the console's checkboxes; {@code permissionIds}
     * are the role's own.
     */
    private Map<String, RoleState> roleStates(UUID tenantId, UUID appId) {
        Map<UUID, String> keys = new HashMap<>();
        permissionAdmin.list(appId).forEach(p -> keys.put(p.id(), p.key()));
        Map<String, RoleState> out = new LinkedHashMap<>();
        for (RoleAdminDtos.RoleSummaryResponse summary : roleAdmin.listRoles(tenantId, appId)) {
            if (!ApplicationRbacScope.isApplicationScopedRoleName(summary.name())) {
                continue;
            }
            RoleAdminDtos.RoleDetailResponse role = roleAdmin.getRole(summary.id(), appId);
            out.put(role.name(), new RoleState(
                    role.id(), role.name(), role.description(), role.isComposite(), role.isSystem(), summary.userCount(),
                    role.permissionIds().stream().map(keys::get).filter(Objects::nonNull).toList(),
                    role.childRoles().stream().map(RoleAdminDtos.RoleRefResponse::name).toList()));
        }
        return out;
    }

    /** Application permission key to the console operator role that holds it (those roles always stay). */
    private Map<String, String> permissionsOfConsoleRoles(UUID tenantId, UUID appId) {
        Map<UUID, String> keys = new HashMap<>();
        permissionAdmin.list(appId).forEach(p -> keys.put(p.id(), p.key()));
        Map<String, String> out = new HashMap<>();
        for (RoleAdminDtos.RoleSummaryResponse summary : roleAdmin.listRoles(tenantId, appId)) {
            if (ApplicationRbacScope.isApplicationScopedRoleName(summary.name())) {
                continue;
            }
            roleAdmin.getRole(summary.id(), appId).permissionIds().stream()
                    .map(keys::get)
                    .filter(Objects::nonNull)
                    .forEach(k -> out.putIfAbsent(k, summary.name()));
        }
        return out;
    }

    private RoleAdminDtos.RoleDetailResponse findRole(UUID tenantId, UUID appId, String name) {
        return roleAdmin.listRoles(tenantId, appId).stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .map(r -> roleAdmin.getRole(r.id(), appId))
                .orElse(null);
    }

    // ---------------------------------------------------------------- helpers

    /** Runs work with the application's schema selected (each service call opens its own transaction there). */
    private static <T> T inApp(UUID applicationId, Supplier<T> work) {
        UUID previous = ApplicationSchemaContext.getApplicationId();
        ApplicationSchemaContext.setApplicationId(applicationId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                ApplicationSchemaContext.clear();
            } else {
                ApplicationSchemaContext.setApplicationId(previous);
            }
        }
    }

    private static Runnable inApp(Ref app, Runnable work) {
        return () -> inApp(app.value, () -> {
            work.run();
            return null;
        });
    }

    private static List<String> describe(Map<String, String> fields) {
        return new TreeSet<>(fields.keySet()).stream().map(k -> k + ": " + fields.get(k)).toList();
    }

    private static List<String> sorted(Collection<String> values) {
        return values == null ? List.of() : new TreeSet<>(values).stream().toList();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list != null ? list : List.of();
    }

    private static <T> Collection<T> nonNull(Collection<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

}
