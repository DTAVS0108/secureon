package com.secureone.auth.configascode;

import com.secureone.auth.audit.AuditService;
import com.secureone.auth.configascode.ConfigPlan.Step;
import com.secureone.auth.configascode.TenantConfigService.Change;
import com.secureone.auth.configascode.TenantConfigService.Result;
import com.secureone.auth.notify.PlatformMailSenderProvider;
import com.secureone.auth.notify.PlatformSmsSettingsService;
import com.secureone.auth.notify.PlatformSmtpSettingsService;
import com.secureone.auth.scim.ScimSettingsService;
import com.secureone.auth.tenant.RlsScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * The platform file (docs/install/configuration-and-upgrades.md): default SMTP server and SMS provider for all applications, and SCIM.
 * Same rules as tenant files: left out means unchanged, credentials come from environment variables.
 */
@Service
public class PlatformConfigService {

    private final PlatformSmtpSettingsService smtp;
    private final PlatformSmsSettingsService sms;
    private final ScimSettingsService scim;
    private final PlatformMailSenderProvider mailSenders;
    private final AuditService audit;
    private Function<String, String> environment = System::getenv;

    public PlatformConfigService(
            PlatformSmtpSettingsService smtp,
            PlatformSmsSettingsService sms,
            ScimSettingsService scim,
            PlatformMailSenderProvider mailSenders,
            AuditService audit) {
        this.smtp = smtp;
        this.sms = sms;
        this.scim = scim;
        this.mailSenders = mailSenders;
        this.audit = audit;
    }

    void setEnvironment(Function<String, String> environment) {
        this.environment = environment;
    }

    public PlatformConfig export() {
        return RlsScope.asPlatform(() -> {
            Map<String, Object> scimConfig = scim.getConfig();
            return new PlatformConfig(
                    TenantConfig.API_VERSION,
                    PlatformConfig.KIND,
                    MessagingConfig.smtpFromView(smtp.storedPublicSettings()),
                    MessagingConfig.smsFromView(sms.getPublicSettings()),
                    new PlatformConfig.Scim(
                            Boolean.TRUE.equals(scimConfig.get("enabled")),
                            scimConfig.get("userNameDomain") != null ? scimConfig.get("userNameDomain").toString() : null,
                            null));
        });
    }

    public Result plan(PlatformConfig config) {
        return RlsScope.asPlatform(() -> {
            ConfigPlan plan = compute(config);
            return result(plan, true, false, plan.changes());
        });
    }

    public Result apply(PlatformConfig config, String actor) {
        return RlsScope.asPlatform(() -> {
            ConfigPlan plan = compute(config);
            if (!plan.errors.isEmpty()) {
                return result(plan, false, false, plan.changes());
            }
            List<Change> done = plan.execute();
            if (!done.isEmpty()) {
                audit.record(null, actor, "config.imported", "platform", null,
                        "platform: " + done.size() + " change(s)" + (plan.errors.isEmpty() ? "" : ", stopped early"),
                        plan.errors.isEmpty());
            }
            return result(plan, false, plan.errors.isEmpty(), done);
        });
    }

    private ConfigPlan compute(PlatformConfig config) {
        ConfigPlan plan = new ConfigPlan(false, environment);
        if (!TenantConfig.API_VERSION.equals(config.apiVersion())) {
            plan.errors.add("apiVersion must be '" + TenantConfig.API_VERSION + "'");
        }
        if (!PlatformConfig.KIND.equals(config.kind())) {
            plan.errors.add("kind must be '" + PlatformConfig.KIND + "'");
        }
        if (!plan.errors.isEmpty()) {
            return plan;
        }
        if (config.smtp() != null) {
            Map<String, Object> stored = smtp.storedPublicSettings();
            boolean hasServer = stored.get("host") != null && !stored.get("host").toString().isBlank();
            MessagingConfig.planSmtp(plan, "platform", config.smtp(), hasServer ? stored : null,
                    smtp::passwordMatches,
                    body -> {
                        smtp.save(body);
                        mailSenders.invalidatePlatform();
                    });
        }
        if (config.sms() != null) {
            MessagingConfig.planSms(plan, "platform", config.sms(), sms.getPublicSettings(),
                    sms::resolveCredentials, sms::save);
        }
        if (config.scim() != null) {
            planScim(plan, config.scim());
        }
        return plan;
    }

    private void planScim(ConfigPlan plan, PlatformConfig.Scim wanted) {
        Map<String, Object> current = scim.getConfig();
        String token = plan.secret(wanted.bearerTokenEnv(), "scim", null, 32);
        List<String> diff = new ArrayList<>();
        ConfigPlan.diffBoolean(diff, "enabled", Boolean.TRUE.equals(current.get("enabled")), wanted.enabled());
        ConfigPlan.diffText(diff, "userNameDomain",
                current.get("userNameDomain") != null ? current.get("userNameDomain").toString() : null,
                wanted.userNameDomain(), true);
        Map<String, Object> body = new LinkedHashMap<>();
        if (wanted.enabled() != null) body.put("enabled", wanted.enabled());
        if (wanted.userNameDomain() != null) body.put("userNameDomain", wanted.userNameDomain());
        if (token != null && !token.equals(scim.bearerToken())) {
            body.put("bearerToken", token);
            diff.add("bearerToken: set from " + wanted.bearerTokenEnv());
        }
        if (!diff.isEmpty()) {
            plan.second.add(new Step(new Change("update", "scim", "platform", diff), () -> scim.saveConfig(body)));
        }
    }

    private static Result result(ConfigPlan plan, boolean dryRun, boolean applied, List<Change> changes) {
        return new Result(dryRun, applied, false, "platform", List.copyOf(plan.errors), List.copyOf(changes),
                List.copyOf(plan.secrets), List.copyOf(plan.notes));
    }
}
