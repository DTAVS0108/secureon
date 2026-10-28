package com.secureone.auth.twostep;

import com.secureone.auth.account.AccountSessionService;
import com.secureone.auth.account.PhoneNormalizer;
import com.secureone.auth.authn.TenantSlugResolver;
import com.secureone.auth.mfa.MfaFactor;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.mfa.MfaPolicyService;
import com.secureone.auth.mfa.MfaRecoveryCodeService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.user.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Two-step verification for end users: which options a user has, what a sign-in needs next (signed in, second
 * step, or setup now / later), the setup grace period, and the changes admins make (reset, extend, exempt).
 */
@Service
public class TwoStepService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> audit;


    private static final Logger log = LoggerFactory.getLogger(TwoStepService.class);

    /** One of a user's second-step options. {@code masked} is the phone or email shown to the user. */
    public record UserMethod(
            UUID factorId, TwoStepMethod method, String label, String masked, boolean isDefault,
            Instant createdAt, Instant lastUsedAt) {

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", factorId.toString());
            out.put("type", method.id());
            out.put("label", label);
            out.put("masked", masked);
            out.put("isDefault", isDefault);
            out.put("strength", method.strength().id());
            out.put("createdAt", createdAt != null ? createdAt.toString() : null);
            out.put("lastUsedAt", lastUsedAt != null ? lastUsedAt.toString() : null);
            return out;
        }
    }

    /** What a sign-in needs after its first step. */
    public sealed interface Decision permits SignedIn, SecondStep, Setup {}

    public record SignedIn() implements Decision {}

    /** Ask for one of {@code methods} (default first); {@code recovery} when backup codes exist. */
    public record SecondStep(List<UserMethod> methods, boolean recovery) implements Decision {}

    /**
     * No usable option yet: set one up now. {@code canSkip} while reminders or the grace period allow it;
     * {@code offered} are the methods the user may set up.
     */
    public record Setup(boolean required, boolean canSkip, Instant deadline, int skipsLeft,
            boolean allowDontAskAgain, List<TwoStepMethod> offered) implements Decision {}

    private final MfaPolicyService policy;
    private final MfaFactorRepository factors;
    private final TwoStepStateStore states;
    private final TrustedDeviceService trustedDevices;
    private final MfaRecoveryCodeService recoveryCodes;
    private final ObjectProvider<EmailNotificationService> mail;
    private final ObjectProvider<AccountSessionService> sessions;
    private final TenantSlugResolver tenantSlugs;
    private final String publicBaseUrl;

    public TwoStepService(
            MfaPolicyService policy,
            MfaFactorRepository factors,
            TwoStepStateStore states,
            TrustedDeviceService trustedDevices,
            MfaRecoveryCodeService recoveryCodes,
            ObjectProvider<EmailNotificationService> mail,
            ObjectProvider<AccountSessionService> sessions,
            TenantSlugResolver tenantSlugs,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.policy = policy;
        this.factors = factors;
        this.states = states;
        this.trustedDevices = trustedDevices;
        this.recoveryCodes = recoveryCodes;
        this.mail = mail;
        this.sessions = sessions;
        this.tenantSlugs = tenantSlugs;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    // ---- The user's options --------------------------------------------------------------------------------

    /** The user's verified second-step options, default first, then strongest. */
    public List<UserMethod> methods(UUID userId) {
        List<MfaFactor> rows = RlsScope.asPlatform(() -> factors.findByUserIdOrderByCreatedAtAsc(userId));
        List<UserMethod> out = new ArrayList<>();
        for (MfaFactor f : rows) {
            if (!f.isVerified()) {
                continue;
            }
            methodOf(f).ifPresent(m -> out.add(new UserMethod(
                    f.getId(), m, labelOf(f, m), maskedOf(f, m), Boolean.TRUE.equals(meta(f).get("isDefault")),
                    f.getCreatedAt(), f.getLastUsedAt())));
        }
        out.sort(Comparator.comparing((UserMethod u) -> !u.isDefault())
                .thenComparing(u -> -u.method().strength().ordinal()));
        return out;
    }

    public static Optional<TwoStepMethod> methodOf(MfaFactor f) {
        return switch (f.getType()) {
            case "totp" -> Optional.of(TwoStepMethod.TOTP);
            case "passkey" -> Optional.of("security_key".equals(meta(f).get("kind"))
                    ? TwoStepMethod.SECURITY_KEY : TwoStepMethod.PASSKEY);
            case "sms" -> Optional.of(TwoStepMethod.SMS);
            case "email" -> Optional.of(TwoStepMethod.EMAIL);
            default -> Optional.empty();
        };
    }

    /** {@code mfa_factor.type} for a method (passkeys and security keys share "passkey"). */
    public static String factorType(TwoStepMethod method) {
        return switch (method) {
            case PASSKEY, SECURITY_KEY -> "passkey";
            case TOTP -> "totp";
            case SMS -> "sms";
            case EMAIL -> "email";
        };
    }

    public Optional<UserMethod> method(UUID userId, UUID factorId) {
        return methods(userId).stream().filter(m -> m.factorId().equals(factorId)).findFirst();
    }

    /** Options of the user that this application allows (and, for "required", that are strong enough). */
    public List<UserMethod> usable(UUID userId, UUID applicationId, String firstFactor, boolean forRequired) {
        List<TwoStepMethod> allowed = policy.allowedMethods(applicationId);
        TwoStepMethod.Strength minimum = policy.minimumStrength(applicationId);
        boolean firstWasSentCode = FactorGrantedAuthority.OTT_AUTHORITY.equals(firstFactor);
        return methods(userId).stream()
                .filter(m -> allowed.contains(m.method()))
                // A code or link first step cannot be completed with another sent code.
                .filter(m -> !(firstWasSentCode && m.method().isSentCode()))
                .filter(m -> !forRequired || m.method().meets(minimum))
                .toList();
    }

    /** Methods a user may set up in this application. */
    public List<TwoStepMethod> offered(UUID applicationId, boolean forRequired) {
        TwoStepMethod.Strength minimum = policy.minimumStrength(applicationId);
        return policy.allowedMethods(applicationId).stream()
                .filter(m -> !forRequired || m.meets(minimum))
                .toList();
    }

    // ---- The decision after a first step -------------------------------------------------------------------

    public Decision decide(UserAccount user, UUID applicationId, String firstFactor, HttpServletRequest request) {
        if (FactorGrantedAuthority.WEBAUTHN_AUTHORITY.equals(firstFactor)) {
            return new SignedIn(); // a verified passkey is both steps
        }
        String mode = policy.loginMfaRequired(applicationId);
        if ("off".equals(mode)) {
            return new SignedIn();
        }
        boolean required = "required".equals(mode);
        // Remembered in this application, within the days it allows now.
        if (request != null
                && trustedDevices.isTrusted(user.getId(), applicationId, policy.rememberDeviceDays(applicationId), request)) {
            return new SignedIn();
        }
        List<UserMethod> usable = usable(user.getId(), applicationId, firstFactor, required);
        if (!usable.isEmpty()) {
            if (!required && !policy.stepUpAfterPassword(applicationId)) {
                return new SignedIn();
            }
            return new SecondStep(preferred(usable, applicationId), recoveryCodes.remaining(user.getId()) > 0);
        }
        Instant now = Instant.now();
        // This application's own setup progress: its deadline, skips and "don't ask again".
        TwoStepStateStore.State state = states.get(user.getId(), applicationId);
        if (exempt(user.getId(), applicationId, now)) {
            return new SignedIn();
        }
        Map<String, Object> p = policy.resolvePolicy(applicationId);
        if (!required) {
            boolean prompt = Boolean.TRUE.equals(p.get("promptOptional"));
            if (!prompt || state.optedOut() || (state.nextPromptAt() != null && now.isBefore(state.nextPromptAt()))) {
                return new SignedIn();
            }
            return new Setup(false, true, null, 0, Boolean.TRUE.equals(p.get("allowDontAskAgain")), offered(applicationId, false));
        }
        Instant deadline = state.deadline();
        if (deadline == null) {
            deadline = firstDeadline(p, now);
            states.save(state.withProgress(now, null, 0, deadline, false, state.extendedBy(), ""));
            state = states.get(user.getId(), applicationId);
        }
        int skipsAllowed = num(p.get("graceSkips"));
        int skipsLeft = Math.max(0, skipsAllowed - state.skipsUsed());
        boolean canSkip = now.isBefore(deadline) && skipsLeft > 0;
        return new Setup(true, canSkip, deadline, skipsLeft, false, offered(applicationId, true));
    }

    /**
     * Exempt in this application, or in every application (the exemption on the row without an application,
     * which also covers sign-ins without one).
     */
    public boolean exempt(UUID userId, UUID applicationId, Instant now) {
        return states.get(userId, applicationId).exempt(now)
                || (applicationId != null && states.get(userId, null).exempt(now));
    }

    /** The exemption that applies here: this application's, else the one for every application. */
    private TwoStepStateStore.State exemption(UUID userId, UUID applicationId, Instant now) {
        TwoStepStateStore.State own = states.get(userId, applicationId);
        if (own.exempt(now) || applicationId == null) {
            return own;
        }
        return states.get(userId, null);
    }

    /** The grace period starts at the first prompt (or the enforce-from date when that is later). */
    static Instant firstDeadline(Map<String, Object> p, Instant now) {
        Instant start = now;
        String enforceFrom = String.valueOf(p.getOrDefault("enforceFrom", ""));
        if (!enforceFrom.isBlank()) {
            Instant from = LocalDate.parse(enforceFrom).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (from.isAfter(start)) {
                start = from;
            }
        }
        return start.plus(Duration.ofDays(num(p.get("graceDays"))));
    }

    /** Default method first; otherwise the policy's preferred method, then the strongest. */
    private List<UserMethod> preferred(List<UserMethod> usable, UUID applicationId) {
        String preferred = String.valueOf(policy.resolvePolicy(applicationId).get("defaultMethod"));
        List<UserMethod> out = new ArrayList<>(usable);
        out.sort(Comparator.comparing((UserMethod u) -> !u.isDefault())
                .thenComparing(u -> !u.method().id().equals(preferred))
                .thenComparing(u -> -u.method().strength().ordinal()));
        return out;
    }

    /** "Later" on the setup screen. Returns false when skipping is not allowed any more. */
    public boolean skip(UserAccount user, UUID applicationId, boolean dontAskAgain) {
        Decision d = decide(user, applicationId, FactorGrantedAuthority.PASSWORD_AUTHORITY, null);
        if (!(d instanceof Setup setup) || !setup.canSkip()) {
            return false;
        }
        // Counted in this application only: another application keeps its own skips and "don't ask again".
        TwoStepStateStore.State s = states.get(user.getId(), applicationId);
        Map<String, Object> p = policy.resolvePolicy(applicationId);
        Instant now = Instant.now();
        if (setup.required()) {
            states.save(s.withProgress(s.firstPromptedAt(), s.nextPromptAt(), s.skipsUsed() + 1, s.deadline(),
                    s.optedOut(), s.extendedBy(), s.remindersSent()));
        } else {
            boolean optOut = dontAskAgain && Boolean.TRUE.equals(p.get("allowDontAskAgain"));
            Instant next = now.plus(Duration.ofDays(num(p.get("promptIntervalDays"))));
            states.save(s.withProgress(s.firstPromptedAt() != null ? s.firstPromptedAt() : now, next, s.skipsUsed(),
                    s.deadline(), optOut || s.optedOut(), s.extendedBy(), s.remindersSent()));
        }
        return true;
    }

    // ---- Changes to the user's options ---------------------------------------------------------------------

    /**
     * A method was added. Makes it the default when it is the only one, issues backup codes when the user has
     * none (returned once), ends any grace period, and emails the user.
     */
    public List<String> methodAdded(UserAccount user, UUID applicationId, UUID factorId, TwoStepMethod method) {
        List<UserMethod> all = methods(user.getId());
        if (all.stream().noneMatch(UserMethod::isDefault)) {
            setDefault(user.getId(), factorId);
        }
        List<String> codes = List.of();
        if (recoveryCodes.remaining(user.getId()) == 0) {
            codes = recoveryCodes.issueForUser(user.getId(), policy.recoveryCodesPerEnrollment(applicationId));
        }
        states.completed(user.getId());
        notifyUser(applicationId, user, method.label() + " was added as a way to verify it's you.");
        auditChange(user, applicationId, "user.two_factor_enrolled", method);
        return codes;
    }

    public void methodRemoved(UserAccount user, UUID applicationId, TwoStepMethod method) {
        if (methods(user.getId()).isEmpty()) {
            recoveryCodes.revokeForUser(user.getId()); // backup codes are for a second step that no longer exists
            trustedDevices.revokeAll(user.getId());
        }
        notifyUser(applicationId, user, method.label() + " was removed from the ways to verify it's you.");
        auditChange(user, applicationId, "two_step.factor_removed", method);
    }

    /** Audit log (and webhooks) for a user's own change to their second-step options. */
    private void auditChange(UserAccount user, UUID applicationId, String action, TwoStepMethod method) {
        if (audit != null) audit.ifAvailable(a -> {
            try {
                a.recordIndependently(user.getTenantId(), applicationId, user.getEmail(), action,
                        "user_account", user.getId(), method.label(), true);
            } catch (RuntimeException e) {
                log.warn("Could not audit {} for {}: {}", action, user.getEmail(), e.getMessage());
            }
        });
    }

    public void setDefault(UUID userId, UUID factorId) {
        for (MfaFactor f : factors.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (methodOf(f).isEmpty()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>(meta(f));
            boolean isDefault = f.getId().equals(factorId);
            if (Boolean.TRUE.equals(meta.get("isDefault")) != isDefault) {
                meta.put("isDefault", isDefault);
                f.setMetadata(meta);
                factors.save(f);
            }
        }
    }

    /** Backup codes used at sign-in: warn when few are left. */
    public void recoveryCodeUsed(UserAccount user, UUID applicationId) {
        int left = recoveryCodes.remaining(user.getId());
        notifyUser(applicationId, user, "A backup code was used to sign in. " + left
                + (left == 1 ? " backup code is" : " backup codes are") + " left"
                + (left <= 3 ? "; get new ones on your account security page." : "."));
    }

    public List<String> regenerateRecoveryCodes(UserAccount user, UUID applicationId) {
        List<String> codes = recoveryCodes.issueForUser(user.getId(), policy.recoveryCodesPerEnrollment(applicationId));
        notifyUser(applicationId, user, "New backup codes were created; the old ones no longer work.");
        return codes;
    }

    /** The user turns two-step verification off (only when it is optional for them). */
    public void turnOff(UserAccount user, UUID applicationId) {
        if ("required".equals(policy.loginMfaRequired(applicationId))) {
            throw new IllegalArgumentException("Two-step verification is required for this application.");
        }
        factors.findByUserIdOrderByCreatedAtAsc(user.getId()).forEach(factors::delete);
        trustedDevices.revokeAll(user.getId());
        notifyUser(applicationId, user, "Two-step verification was turned off.");
    }

    // ---- Admin actions -------------------------------------------------------------------------------------

    /**
     * Removes one method (or all), ends the user's sessions and forgets their devices. With all removed and
     * two-step verification required, the next sign-in starts setup with no grace period.
     */
    public void reset(UserAccount user, UUID applicationId, TwoStepMethod onlyMethod, String byWhom) {
        for (MfaFactor f : factors.findByUserIdOrderByCreatedAtAsc(user.getId())) {
            boolean match = onlyMethod == null
                    ? true
                    : methodOf(f).map(m -> m == onlyMethod).orElse(false);
            if (match) {
                factors.delete(f);
            }
        }
        if (onlyMethod == null || methods(user.getId()).isEmpty()) {
            recoveryCodes.revokeForUser(user.getId());
            // No grace period in the application the reset was made from.
            TwoStepStateStore.State s = states.get(user.getId(), applicationId);
            states.save(s.withProgress(Instant.now(), null, 0, Instant.now(), false, s.extendedBy(), ""));
        }
        trustedDevices.revokeAll(user.getId());
        endSessions(user);
        notifyUser(applicationId, user, (onlyMethod == null ? "An administrator reset your two-step verification"
                : "An administrator removed " + onlyMethod.label() + " from your account")
                + ". You were signed out everywhere; you'll set it up again at your next sign-in.");
    }

    /**
     * More time to set up, in one application ({@code applicationId}), or in every application where the user
     * has a deadline ({@code null}; also the row without an application).
     */
    public void extend(UserAccount user, UUID applicationId, int days, String byWhom) {
        List<TwoStepStateStore.State> rows = applicationId != null
                ? List.of(states.get(user.getId(), applicationId))
                : withNoApplicationRow(user.getId(), states.forUser(user.getId()).stream()
                        .filter(s -> s.deadline() != null || s.applicationId() == null).toList());
        Instant now = Instant.now();
        for (TwoStepStateStore.State s : rows) {
            Instant base = s.deadline() != null && s.deadline().isAfter(now) ? s.deadline() : now;
            states.save(s.withProgress(s.firstPromptedAt() != null ? s.firstPromptedAt() : now, s.nextPromptAt(), 0,
                    base.plus(Duration.ofDays(days)), s.optedOut(), byWhom, ""));
        }
    }

    private List<TwoStepStateStore.State> withNoApplicationRow(UUID userId, List<TwoStepStateStore.State> rows) {
        if (rows.stream().anyMatch(s -> s.applicationId() == null)) {
            return rows;
        }
        List<TwoStepStateStore.State> out = new ArrayList<>(rows);
        out.add(states.get(userId, null));
        return out;
    }

    /** Exempts in one application, or in every application ({@code applicationId} null). {@code until} null ends it. */
    public void exempt(UserAccount user, UUID applicationId, Instant until, String reason, String byWhom) {
        TwoStepStateStore.State s = states.get(user.getId(), applicationId);
        states.save(s.withExemption(until, until != null ? reason : null, until != null ? byWhom : null));
    }

    /** Who already extended this user's deadline in this application (one extension for tenant admins). */
    public String extendedBy(UUID userId, UUID applicationId) {
        return states.get(userId, applicationId).extendedBy();
    }

    /** Two-factor is off for this application (e.g. its Two Factor Authentication switch is off): nothing to manage. */
    public boolean offFor(UUID applicationId) {
        return "off".equals(policy.loginMfaRequired(applicationId));
    }

    /** Status shown to the user and to admins. */
    public Map<String, Object> status(UserAccount user, UUID applicationId) {
        List<UserMethod> all = methods(user.getId());
        String mode = policy.loginMfaRequired(applicationId);
        boolean required = "required".equals(mode);
        List<UserMethod> qualifying = usable(user.getId(), applicationId, null, required);
        TwoStepStateStore.State s = states.get(user.getId(), applicationId);
        Instant now = Instant.now();
        TwoStepStateStore.State ex = exemption(user.getId(), applicationId, now);
        String status;
        if (!qualifying.isEmpty()) {
            status = "enabled";
        } else if (ex.exempt(now)) {
            status = "exempt";
        } else if (required) {
            status = s.deadline() == null ? "not_set_up" : s.deadline().isAfter(now) ? "in_grace" : "overdue";
        } else {
            status = all.isEmpty() ? "off" : "enabled";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("mode", mode);
        out.put("minimumStrength", policy.minimumStrength(applicationId).id());
        out.put("methods", all.stream().map(UserMethod::toMap).toList());
        out.put("allowedMethods", policy.allowedMethods(applicationId).stream().map(TwoStepMethod::id).toList());
        out.put("offeredMethods", offered(applicationId, required).stream().map(TwoStepMethod::id).toList());
        out.put("backupCodesRemaining", recoveryCodes.remaining(user.getId()));
        out.put("deadline", s.deadline() != null ? s.deadline().toString() : null);
        out.put("skipsUsed", s.skipsUsed());
        out.put("exemptUntil", ex.exempt(now) ? ex.exemptUntil().toString() : null);
        out.put("exemptReason", ex.exempt(now) ? ex.exemptReason() : null);
        out.put("exemptedBy", ex.exempt(now) ? ex.exemptedBy() : null);
        // "application": this application only; "all": every application of the tenant.
        out.put("exemptScope", ex.exempt(now) ? (ex.applicationId() != null ? "application" : "all") : null);
        out.put("extendedBy", s.extendedBy());
        return out;
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    public void notifyUser(UUID applicationId, UserAccount user, String event) {
        try {
            String link = publicBaseUrl + "/account/security.html" + (applicationId != null ? "?applicationId=" + applicationId : "");
            mail.getObject().sendSecurityNotice(applicationId, user, event, link);
        } catch (RuntimeException ex) {
            log.warn("Could not send security notice to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    public void endSessions(UserAccount user) {
        try {
            String principal = tenantSlugs.slugForTenant(user.getTenantId()) + ":" + user.getEmail();
            sessions.getObject().revokeOthers(principal, null);
        } catch (RuntimeException ex) {
            log.warn("Could not end sessions of {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> meta(MfaFactor f) {
        return f.getMetadata() != null ? f.getMetadata() : Map.of();
    }

    private static String labelOf(MfaFactor f, TwoStepMethod m) {
        String label = f.getLabel();
        return label != null && !label.isBlank() ? label : m.label();
    }

    private static String maskedOf(MfaFactor f, TwoStepMethod m) {
        Object destination = meta(f).get("destination");
        if (destination == null) {
            return null;
        }
        String d = destination.toString();
        if (m == TwoStepMethod.SMS) {
            return PhoneNormalizer.mask(d);
        }
        int at = d.indexOf('@');
        return at > 1 ? d.charAt(0) + "•••" + d.substring(at) : d;
    }

    private static int num(Object raw) {
        return raw instanceof Number n ? n.intValue() : 0;
    }
}
