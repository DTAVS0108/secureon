package com.secureone.auth.setup;

import com.secureone.auth.account.UserPasswordService;
import com.secureone.auth.admin.platform.PlatformOperators;
import com.secureone.auth.admin.user.UserAdminDtos.UserCreateRequest;
import com.secureone.auth.admin.user.UserAdminService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * First-run setup: while no platform operator exists, whoever holds the setup code can create the
 * first one (an account in the reserved platform tenant with the platform-admin role). The code is
 * printed in the server log at start-up, or set with {@code SECUREONE_SETUP_CODE} for automated installs;
 * it proves access to the server, like an initial admin password. Once an operator exists, setup is
 * closed for good and the console sign-in (with MFA enrolment) takes over.
 *
 * <p>For installs without a browser step (like Keycloak's {@code KC_BOOTSTRAP_ADMIN_*}),
 * {@code SECUREONE_BOOTSTRAP_ADMIN_EMAIL} and {@code SECUREONE_BOOTSTRAP_ADMIN_PASSWORD} create that first
 * operator when the server starts. They are used once: when an operator exists they are ignored. That password
 * is temporary: the operator chooses their own at the first sign-in, which is stored only in the database.
 */
@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    static final String CODE_KEY = "setup:code";
    static final String LOCK_KEY = "setup:lock";
    static final Duration CODE_TTL = Duration.ofHours(24);
    static final int MAX_ATTEMPTS = 10;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final SecretProtector secrets;
    private final PlatformOperators operators;
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final UserAdminService userAdmin;
    private final UserPasswordService passwords;
    private final AuditService audit;
    private final String configuredCode;
    private final String adminWebUrl;
    private final Bootstrap bootstrap;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    public SetupService(
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            SecretProtector secrets,
            PlatformOperators operators,
            TenantRepository tenants,
            UserAccountRepository users,
            UserAdminService userAdmin,
            UserPasswordService passwords,
            AuditService audit,
            @Value("${SECUREONE_SETUP_CODE:}") String configuredCode,
            @Value("${secureone.admin-web-url:http://localhost:9000/admin}") String adminWebUrl,
            @Value("${SECUREONE_BOOTSTRAP_ADMIN_EMAIL:}") String bootstrapEmail,
            @Value("${SECUREONE_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword,
            @Value("${SECUREONE_BOOTSTRAP_ADMIN_FIRST_NAME:}") String bootstrapFirstName,
            @Value("${SECUREONE_BOOTSTRAP_ADMIN_LAST_NAME:}") String bootstrapLastName,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.secrets = secrets;
        this.operators = operators;
        this.tenants = tenants;
        this.users = users;
        this.userAdmin = userAdmin;
        this.passwords = passwords;
        this.audit = audit;
        this.configuredCode = configuredCode == null ? "" : configuredCode.trim();
        this.adminWebUrl = adminWebUrl.replaceAll("/+$", "");
        this.bootstrap = new Bootstrap(trim(bootstrapEmail), bootstrapPassword, trim(bootstrapFirstName), trim(bootstrapLastName));
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** First operator from SECUREONE_BOOTSTRAP_ADMIN_*; password is never logged. */
    record Bootstrap(String email, String password, String firstName, String lastName) {
        boolean requested() {
            return email != null && !email.isEmpty();
        }
    }

    /**
     * Creates the first platform operator from SECUREONE_BOOTSTRAP_ADMIN_* when none exists. Runs before
     * the setup announcement. A value that cannot be used (bad email, password against the policy) stops
     * the server with the reason, so an automated install never comes up without the admin it asked for.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void bootstrapFromEnvironment() {
        if (!bootstrap.requested()) {
            return;
        }
        RlsScope.runAsPlatform(() -> {
            if (!setupRequired()) {
                log.info("SECUREONE_BOOTSTRAP_ADMIN_EMAIL is set, but a platform operator already exists: ignored."
                        + " You can remove the SECUREONE_BOOTSTRAP_ADMIN_* settings.");
                return;
            }
            if (bootstrap.password() == null || bootstrap.password().isBlank()) {
                throw new IllegalStateException(
                        "SECUREONE_BOOTSTRAP_ADMIN_EMAIL is set without SECUREONE_BOOTSTRAP_ADMIN_PASSWORD.");
            }
            String email;
            try {
                email = transactions.execute(status -> createFirstOperator(
                        bootstrap.email(), bootstrap.firstName(), bootstrap.lastName(), bootstrap.password(),
                        "SECUREONE_BOOTSTRAP_ADMIN_*", true));
            } catch (SetupClosedException e) {
                log.info("Another instance is creating the first platform operator; skipped here.");
                return;
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Could not create the first platform operator from SECUREONE_BOOTSTRAP_ADMIN_*: " + e.getMessage(), e);
            }
            String banner = "=".repeat(78);
            log.warn("\n{}\n First platform operator created: {} (from SECUREONE_BOOTSTRAP_ADMIN_*).\n Sign in at {}/login"
                            + " (Platform operator tab); the first sign-in asks for a new password.\n"
                            + " SECUREONE_BOOTSTRAP_ADMIN_PASSWORD is not used again and can be removed.\n{}",
                    banner, email, adminWebUrl, banner);
        });
    }

    /** True until the first platform operator exists. */
    public boolean setupRequired() {
        Integer count = RlsScope.asPlatform(
                () -> jdbc.queryForObject("SELECT count(*) FROM platform.platform_operator", Integer.class));
        return count == null || count == 0;
    }

    public boolean codeFromEnvironment() {
        return !configuredCode.isEmpty();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void announce() {
        try {
            if (!setupRequired()) {
                return;
            }
            String banner = "=".repeat(78);
            if (codeFromEnvironment()) {
                log.warn("\n{}\n SecureOne is not set up yet: no platform operator exists.\n Open {}/setup and enter the setup code from SECUREONE_SETUP_CODE.\n{}",
                        banner, adminWebUrl, banner);
            } else {
                log.warn("\n{}\n SecureOne is not set up yet: no platform operator exists.\n Open {}/setup and enter this setup code:  {}\n (valid for 24 hours; every instance prints it again when it starts)\n{}",
                        banner, adminWebUrl, activeCode(), banner);
            }
        } catch (RuntimeException e) {
            log.warn("Could not check first-run setup: {}", e.getMessage());
        }
    }

    /** The setup code in force: from the environment, or the one stored (encrypted) in Redis, or a new one. */
    String activeCode() {
        if (codeFromEnvironment()) {
            return configuredCode;
        }
        String stored = redis.opsForValue().get(CODE_KEY);
        if (stored != null) {
            return secrets.decrypt(stored);
        }
        String code = newCode();
        Boolean created = redis.opsForValue().setIfAbsent(CODE_KEY, secrets.encrypt(code), CODE_TTL);
        if (Boolean.TRUE.equals(created)) {
            return code;
        }
        // Another instance created one at the same moment: use that.
        return secrets.decrypt(redis.opsForValue().get(CODE_KEY));
    }

    /** Checks a setup code, counting failed attempts per client address. */
    public void verify(String code, String clientAddress) {
        if (!setupRequired()) {
            throw new SetupClosedException();
        }
        String attemptsKey = "setup:attempts:" + (clientAddress == null ? "unknown" : clientAddress);
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptsKey, ATTEMPT_WINDOW);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            throw new TooManySetupAttemptsException();
        }
        if (!matches(code)) {
            throw new IllegalArgumentException("That setup code is not valid. Copy it from the server log (or SECUREONE_SETUP_CODE).");
        }
        redis.delete(attemptsKey);
    }

    private boolean matches(String code) {
        if (code == null) {
            return false;
        }
        String expected = normalize(activeCode());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), normalize(code).getBytes(StandardCharsets.UTF_8));
    }

    public record CompleteRequest(String code, String email, String firstName, String lastName, String password) {}

    /** Creates the first platform operator. Must run inside {@link RlsScope#asPlatform}. */
    @Transactional
    public String complete(CompleteRequest request, String clientAddress) {
        verify(request.code(), clientAddress);
        return createFirstOperator(request.email(), request.firstName(), request.lastName(), request.password(),
                "first-run setup", false);
    }

    /**
     * The first operator, once: in the platform tenant, with the platform-admin role. In a transaction.
     * {@code temporary}: the password came from the configuration (bootstrap), so the operator replaces it at
     * the first sign-in; from then on only the database holds their password.
     */
    private String createFirstOperator(
            String rawEmail, String firstName, String lastName, String password, String via, boolean temporary) {
        if (rawEmail == null || !rawEmail.contains("@")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Choose a password.");
        }
        Boolean locked = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(1));
        if (!Boolean.TRUE.equals(locked)) {
            throw new SetupClosedException();
        }
        try {
            if (!setupRequired()) {
                throw new SetupClosedException();
            }
            Tenant platform = tenants.findBySlug(PlatformOperators.PLATFORM_TENANT_SLUG)
                    .orElseThrow(() -> new IllegalStateException("Reserved platform tenant is missing (migration V62)"));
            String email = rawEmail.trim().toLowerCase(Locale.ROOT);
            // The account may already exist (an operator whose role was removed): use it rather than fail on a
            // duplicate, which would stop a server that has no operator from ever starting.
            UserAccount user = users.findByTenantIdAndEmail(platform.getId(), email).orElse(null);
            boolean passwordAlreadySet = false;
            if (user == null) {
                userAdmin.create(new UserCreateRequest(
                        platform.getId(), email, null, trim(firstName), trim(lastName), "ACTIVE", null, null));
                user = users.findByTenantIdAndEmail(platform.getId(), email)
                        .orElseThrow(() -> new IllegalStateException("Operator account was not created"));
            } else {
                log.warn("First platform operator ({}): the account {} already exists; it becomes the operator again.",
                        via, email);
                // Same password as now (typically the bootstrap settings that created it): setting it again would
                // only be refused by password history.
                passwordAlreadySet = passwords.isCurrentPassword(user.getId(), password);
            }
            // Platform password policy applies; a rejected password rolls the whole setup back.
            if (!passwordAlreadySet) {
                passwords.setPassword(user.getId(), password, null, temporary);
            }
            operators.grant(user.getId(), via);
            audit.record(platform.getId(), email, "platform.setup_completed", "user_account", user.getId(), via, true);
            redis.delete(CODE_KEY);
            log.info("First-run setup complete ({}): {} is the first platform operator.", via, email);
            return email;
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    private String newCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static String normalize(String code) {
        return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** Setup is finished (an operator exists): 410 Gone. */
    public static class SetupClosedException extends RuntimeException {
        SetupClosedException() {
            super("SecureOne is already set up. Sign in to the console.");
        }
    }

    /** Too many wrong codes from one address: 429. */
    public static class TooManySetupAttemptsException extends RuntimeException {
        TooManySetupAttemptsException() {
            super("Too many wrong setup codes. Wait 15 minutes and try again.");
        }
    }
}
