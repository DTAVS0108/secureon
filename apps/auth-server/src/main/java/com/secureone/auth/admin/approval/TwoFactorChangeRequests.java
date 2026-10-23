package com.secureone.auth.admin.approval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.admin.AdminStepUpService;
import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.approval.ChangeRequests.Row;
import com.secureone.auth.admin.approval.ChangeRequests.Status;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.SettingsExposureService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.twostep.TwoStepPolicyResolver;
import com.secureone.auth.twostep.TwoStepService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two Factor Authentication changes that weaken protection wait for a second admin (docs/plans/change-approvals.md):
 * an application turning it off, a weaker application, tenant or platform policy (or an unlocked setting), closing
 * it to applications, and exempting a user. Strengthening changes never come here.
 *
 * <p>While nobody else could approve (for example a single platform operator), the change applies at once after a
 * fresh second step, recorded as "applied without approval"; Access review shows that approvals are not active.
 */
@Service
public class TwoFactorChangeRequests {

    public static final String APP_OFF = "two_factor.app_off";
    public static final String APP_POLICY = "two_factor.app_policy";
    public static final String TENANT_POLICY = "two_factor.tenant_policy";
    public static final String PLATFORM_POLICY = "two_factor.platform_policy";
    public static final String PLATFORM_EXPOSURE = "two_factor.platform_exposure";
    public static final String USER_EXEMPT = "two_factor.user_exempt";

    /** Requests only platform operators decide (a tenant super admin cannot approve their own tenant's defaults). */
    public static final Set<String> PLATFORM_DECIDES = Set.of(TENANT_POLICY, PLATFORM_POLICY, PLATFORM_EXPOSURE);

    /** {@code target_id} of platform-wide requests. */
    static final UUID PLATFORM_TARGET = new UUID(0L, 0L);

    static final String NO_APPROVER_NOTE =
            "Applied without approval: nobody else can approve yet (add a second platform operator or tenant super admin).";

    /** The change applied now ({@code applied}), or waits for approval. */
    public record Outcome(boolean applied, Row request) {
        public boolean pending() {
            return !applied;
        }
    }

    private final ChangeRequests requests;
    private final AuditService audit;
    private final AdminStepUpService stepUp;
    private final ApplicationSettingsService appSettings;
    private final TwoStepPolicyResolver resolver;
    private final PlatformSettingsService platformSettings;
    private final SettingsExposureService exposure;
    private final TwoStepService twoStep;
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    /**
     * Reads across tenants (platform operators, application and tenant names) in their own platform-scoped
     * transaction: the request's transaction may already be limited to the application's tenant.
     */
    private org.springframework.transaction.support.TransactionTemplate platformRead;

    @org.springframework.beans.factory.annotation.Autowired
    void setTransactions(org.springframework.transaction.PlatformTransactionManager transactions) {
        this.platformRead = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.platformRead.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.platformRead.setReadOnly(true);
    }

    private <T> T readAsPlatform(java.util.function.Supplier<T> read) {
        return RlsScope.asPlatform(() -> platformRead.execute(status -> read.get()));
    }

    public TwoFactorChangeRequests(
            ChangeRequests requests,
            AuditService audit,
            AdminStepUpService stepUp,
            ApplicationSettingsService appSettings,
            TwoStepPolicyResolver resolver,
            PlatformSettingsService platformSettings,
            SettingsExposureService exposure,
            TwoStepService twoStep,
            UserAccountRepository users,
            JdbcTemplate jdbc,
            ObjectMapper json) {
        this.requests = requests;
        this.audit = audit;
        this.stepUp = stepUp;
        this.appSettings = appSettings;
        this.resolver = resolver;
        this.platformSettings = platformSettings;
        this.exposure = exposure;
        this.twoStep = twoStep;
        this.users = users;
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---- Requests ----------------------------------------------------------------------------------------------

    /** The application's Two Factor Authentication switch on → off. */
    @Transactional
    public Outcome appOff(UUID applicationId, String requester, String reason, HttpServletRequest request) {
        String app = applicationName(applicationId);
        return submit(APP_OFF, resolver.tenantOf(applicationId), applicationId, applicationId, app,
                Map.of("before", Map.of("tabEnabled", true)),
                "Turn Two Factor Authentication off for " + app + " (no second step at sign-in)",
                requester, reason, request,
                () -> appSettings.saveMfaTabEnabled(applicationId, false));
    }

    /** A weaker application policy. {@code values} is what the admin saved; {@code weakenings} what it weakens. */
    @Transactional
    public Outcome appPolicy(UUID applicationId, Map<String, Object> values, List<String> weakenings,
            String requester, String reason, HttpServletRequest request) {
        String app = applicationName(applicationId);
        return submit(APP_POLICY, resolver.tenantOf(applicationId), applicationId, applicationId, app,
                payload(values, policySnapshot(appSettings.resolveMfaPolicy(applicationId))),
                "Weaker Two Factor Authentication for " + app + ": " + String.join("; ", weakenings),
                requester, reason, request,
                () -> appSettings.saveMfaPolicy(applicationId, values));
    }

    /** Weaker tenant defaults or an unlocked setting. {@code changes} is what the tenant stores. */
    @Transactional
    public Outcome tenantPolicy(UUID tenantId, Map<String, Object> changes, List<String> weakenings,
            String requester, String reason, HttpServletRequest request) {
        String tenant = tenantName(tenantId);
        return submit(TENANT_POLICY, tenantId, null, tenantId, tenant,
                payload(changes, policySnapshot(resolver.tenantOverrides(tenantId))),
                "Weaker Two Factor Authentication defaults for tenant " + tenant + ": " + String.join("; ", weakenings),
                requester, reason, request,
                () -> resolver.saveTenant(tenantId, changes));
    }

    /** A weaker platform policy or an unlocked setting. {@code policy} is the complete policy to save. */
    @Transactional
    public Outcome platformPolicy(Map<String, Object> policy, List<String> weakenings,
            String requester, String reason, HttpServletRequest request) {
        return submit(PLATFORM_POLICY, null, null, PLATFORM_TARGET, "Platform",
                payload(policy, policySnapshot(resolver.platform())),
                "Weaker platform Two Factor Authentication: " + String.join("; ", weakenings),
                requester, reason, request,
                () -> platformSettings.saveMap(TwoStepPolicyResolver.KEY, policy));
    }

    /** Platform settings → For applications closes Two Factor Authentication (with the rest of {@code body}). */
    @Transactional
    public Outcome closeToApplications(Map<String, Boolean> body, String requester, String reason,
            HttpServletRequest request) {
        return submit(PLATFORM_EXPOSURE, null, null, PLATFORM_TARGET, "For applications",
                payload(body, Map.of(TwoStepPolicyResolver.OWN_SETTINGS_EXPOSURE, true)),
                "Close Two Factor Authentication to applications: every application uses the platform and tenant settings",
                requester, reason, request,
                () -> exposure.saveExposure(body));
    }

    /** Exempts a user from the second step, in one application or ({@code applicationId} null) every one. */
    @Transactional
    public Outcome exempt(UserAccount user, UUID applicationId, Instant until, String exemptReason,
            String requester, HttpServletRequest request) {
        String where = applicationId == null ? "every application" : applicationName(applicationId);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("applicationId", applicationId != null ? applicationId.toString() : null);
        values.put("until", until.toString());
        values.put("reason", exemptReason);
        return submit(USER_EXEMPT, user.getTenantId(), applicationId, user.getId(), user.getEmail(),
                payload(values, Map.of()),
                "Exempt " + user.getEmail() + " from Two Factor Authentication in " + where + " until "
                        + until.toString().substring(0, 10) + ": " + exemptReason,
                requester, exemptReason, request,
                () -> applyExempt(user, applicationId, until, exemptReason, requester));
    }

    /** The pending request of {@code action} for {@code targetId}, if any (shown next to the setting). */
    public java.util.Optional<Row> pending(String action, UUID targetId) {
        return requests.findPending(action, targetId == null ? PLATFORM_TARGET : targetId);
    }

    // ---- Approval --------------------------------------------------------------------------------------------

    /** Applies an approved request; returns why it could not be applied (it then fails), or null. */
    public String apply(Row row) {
        Map<String, Object> payload = read(row.payload());
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) payload.getOrDefault("values", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) payload.getOrDefault("before", Map.of());
        switch (row.action()) {
            case APP_OFF -> {
                if (!Boolean.TRUE.equals(appSettings.getMfaTabState(row.applicationId()).get("tabEnabled"))) {
                    return "Two Factor Authentication is already off for this application.";
                }
                appSettings.saveMfaTabEnabled(row.applicationId(), false);
            }
            case APP_POLICY -> {
                if (!policySnapshot(appSettings.resolveMfaPolicy(row.applicationId())).equals(normalize(before))) {
                    return stale();
                }
                appSettings.saveMfaPolicy(row.applicationId(), values);
            }
            case TENANT_POLICY -> {
                if (!policySnapshot(resolver.tenantOverrides(row.tenantId())).equals(normalize(before))) {
                    return stale();
                }
                resolver.saveTenant(row.tenantId(), values);
            }
            case PLATFORM_POLICY -> {
                if (!policySnapshot(resolver.platform()).equals(normalize(before))) {
                    return stale();
                }
                platformSettings.saveMap(TwoStepPolicyResolver.KEY, values);
            }
            case PLATFORM_EXPOSURE -> {
                if (!exposure.isExposed(TwoStepPolicyResolver.OWN_SETTINGS_EXPOSURE)) {
                    return "Two Factor Authentication is already closed to applications.";
                }
                Map<String, Boolean> body = new LinkedHashMap<>();
                values.forEach((k, v) -> body.put(k, Boolean.TRUE.equals(v)));
                exposure.saveExposure(body);
            }
            case USER_EXEMPT -> {
                UserAccount user = RlsScope.asPlatform(() -> users.findById(row.targetId())).orElse(null);
                if (user == null) {
                    return "The user no longer exists.";
                }
                Object app = values.get("applicationId");
                applyExempt(user, app != null ? UUID.fromString(app.toString()) : null,
                        Instant.parse(String.valueOf(values.get("until"))), String.valueOf(values.get("reason")),
                        row.requestedBy());
            }
            default -> throw new IllegalStateException("Unknown change: " + row.action());
        }
        return null;
    }

    // ---- Who else could approve ------------------------------------------------------------------------------

    /**
     * Whether someone other than {@code requester} (and other than {@code excludedEmail}, the person a change is
     * about) could approve: an active platform operator, or for application and user changes a tenant super admin.
     */
    public boolean otherApproverExists(String action, UUID tenantId, String requester, String excludedEmail) {
        String me = requester == null ? "" : requester;
        String them = excludedEmail == null ? "" : excludedEmail;
        return readAsPlatform(() -> {
            Integer operators = jdbc.queryForObject(
                    """
                    SELECT count(*) FROM platform.platform_operator p JOIN platform.user_account u ON u.id = p.user_id
                    WHERE u.status = 'ACTIVE' AND lower(u.email) <> lower(?) AND lower(u.email) <> lower(?)
                    """,
                    Integer.class, me, them);
            if (operators != null && operators > 0) {
                return true;
            }
            if (PLATFORM_DECIDES.contains(action) || tenantId == null) {
                return false;
            }
            Integer superAdmins = jdbc.queryForObject(
                    """
                    SELECT count(DISTINCT a.user_id) FROM platform.admin_console_access a
                    JOIN platform.user_account u ON u.id = a.user_id
                    WHERE a.tenant_id = ? AND a.role_type = 'TENANT_SUPER_ADMIN'
                      AND (a.expires_at IS NULL OR a.expires_at > now()) AND u.status = 'ACTIVE'
                      AND lower(u.email) <> lower(?) AND lower(u.email) <> lower(?)
                    """,
                    Integer.class, tenantId, me, them);
            return superAdmins != null && superAdmins > 0;
        });
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private Outcome submit(String action, UUID tenantId, UUID applicationId, UUID targetId, String label,
            Map<String, Object> payload, String details, String requester, String reason,
            HttpServletRequest request, Runnable applyNow) {
        if (requests.findPending(action, targetId).isPresent()) {
            throw new ConflictException("A change to " + label + " is already waiting for approval on Access review. "
                    + "Withdraw it there, or wait for the decision.");
        }
        String excluded = USER_EXEMPT.equals(action) ? label : null;
        String payloadJson = write(payload);
        if (!otherApproverExists(action, tenantId, requester, excluded)) {
            stepUp.require(request); // applying without a second admin needs a fresh second step
            applyNow.run();
            Row row = requests.create(action, tenantId, applicationId, targetId, label, trim(reason), requester,
                    payloadJson, details);
            requests.decide(row.id(), Status.APPROVED, requester, NO_APPROVER_NOTE);
            audit.record(tenantId, applicationId, requester, "change_request.applied_without_approval",
                    "two_factor", targetId, details, true);
            return new Outcome(true, requests.find(row.id()).orElseThrow());
        }
        Row row = requests.create(action, tenantId, applicationId, targetId, label, trim(reason), requester,
                payloadJson, details);
        audit.record(tenantId, applicationId, requester, "change_request.requested", "two_factor", targetId, details, true);
        return new Outcome(false, row);
    }

    private void applyExempt(UserAccount user, UUID applicationId, Instant until, String reason, String byWhom) {
        twoStep.exempt(user, applicationId, until, reason, byWhom);
        twoStep.notifyUser(applicationId, user, "An administrator exempted you from two-step verification"
                + (applicationId == null ? "" : " in this application") + " until " + until.toString().substring(0, 10) + ".");
    }

    private static Map<String, Object> payload(Map<String, ?> values, Map<String, ?> before) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("values", values);
        out.put("before", before);
        return out;
    }

    /** The settings that matter for a stale check, in a form that survives a JSON round trip. */
    private Map<String, Object> policySnapshot(Map<String, Object> policy) {
        Map<String, Object> out = new LinkedHashMap<>();
        policy.forEach((k, v) -> {
            if (MfaPolicyDefaults.LOCKABLE.contains(k) || MfaPolicyDefaults.LOCKS.equals(k)) {
                out.put(k, v);
            }
        });
        return normalize(out);
    }

    private Map<String, Object> normalize(Map<String, Object> map) {
        return read(write(map == null ? Map.of() : new java.util.TreeMap<>(map)));
    }

    private static String stale() {
        return "The setting changed after this request was made; ask again with the current values.";
    }

    private String applicationName(UUID applicationId) {
        return readAsPlatform(() -> jdbc.query("SELECT name FROM platform.application WHERE id = ?",
                        (rs, i) -> rs.getString(1), applicationId)
                .stream().findFirst().orElse(String.valueOf(applicationId)));
    }

    private String tenantName(UUID tenantId) {
        return readAsPlatform(() -> jdbc.query("SELECT name FROM platform.tenant WHERE id = ?",
                        (rs, i) -> rs.getString(1), tenantId)
                .stream().findFirst().orElse(String.valueOf(tenantId)));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not record the change", ex);
        }
    }

    private Map<String, Object> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(raw, new TypeReference<java.util.TreeMap<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Unreadable change request", ex);
        }
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
