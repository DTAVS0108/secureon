package com.secureone.auth.setup;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.secureone.auth.authn.DevAdminAuthenticationProvider;
import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.notify.EncryptionKeySource;
import com.secureone.auth.notify.PlatformMailSenderProvider;
import com.secureone.auth.notify.PlatformSmtpSettingsService;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** First-run setup API (public; only does anything while no platform operator exists). */
@Tag(name = "First-run setup", description = "Creates the first platform operator on a new installation (setup code)")
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setup;
    private final ObjectProvider<DevAdminAuthenticationProvider> emergencyAccount;
    private final boolean adminMfaRequired;
    private final EncryptionKeySource encryptionKey;
    private final PlatformSmtpSettingsService smtp;
    private final PlatformMailSenderProvider mailSenders;
    private final String publicBaseUrl;

    public SetupController(
            SetupService setup,
            ObjectProvider<DevAdminAuthenticationProvider> emergencyAccount,
            @Value("${secureone.admin.mfa-required:false}") boolean adminMfaRequired,
            EncryptionKeySource encryptionKey,
            PlatformSmtpSettingsService smtp,
            PlatformMailSenderProvider mailSenders,
            @Value("${secureone.public-base-url:}") String publicBaseUrl) {
        this.setup = setup;
        this.emergencyAccount = emergencyAccount;
        this.adminMfaRequired = adminMfaRequired;
        this.encryptionKey = encryptionKey;
        this.smtp = smtp;
        this.mailSenders = mailSenders;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * {@code emergencyAccess}: whether the console sign-in page offers the break-glass account.
     * {@code adminMfaRequired}: whether console sign-in asks for an authenticator app.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean required = setup.setupRequired();
        return Map.of(
                "setupRequired", required,
                "codeFromEnvironment", required && setup.codeFromEnvironment(),
                "emergencyAccess", emergencyAccount.getIfAvailable() != null,
                "adminMfaRequired", adminMfaRequired);
    }

    public record VerifyRequest(String code) {}

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@RequestBody VerifyRequest body, HttpServletRequest request) {
        setup.verify(body == null ? null : body.code(), ClientIpResolver.resolve(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * What the setup wizard shows after the code: the public address, where the encryption key is kept and whether
     * email is set up. A key in the data volume is included once here, for the backup step: whoever holds the setup
     * code can read the server's files anyway. After setup it is never served again.
     */
    @PostMapping("/installation")
    public Map<String, Object> installation(@RequestBody VerifyRequest body, HttpServletRequest request) {
        setup.verify(body == null ? null : body.code(), ClientIpResolver.resolve(request));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publicUrl", publicBaseUrl);
        out.put("https", publicBaseUrl.toLowerCase(Locale.ROOT).startsWith("https://"));
        out.put("encryptionKeySource", encryptionKey.origin().name().toLowerCase(Locale.ROOT));
        if (encryptionKey.inFile()) {
            out.put("encryptionKeyFile", encryptionKey.file().toString());
            out.put("encryptionKey", encryptionKey.value());
        }
        out.put("emailConfigured", RlsScope.asPlatform(smtp::isConfigured));
        return out;
    }

    public record EmailRequest(
            String code, String host, Integer port, String security, String username, String password, String testTo) {}

    /** Saves the platform SMTP server (as Settings › Notifications does) and, with {@code testTo}, sends a test message. */
    @PostMapping("/email")
    public Map<String, Object> email(@RequestBody EmailRequest body, HttpServletRequest request) {
        if (body == null) {
            throw new IllegalArgumentException("Enter the SMTP server.");
        }
        setup.verify(body.code(), ClientIpResolver.resolve(request));
        if (body.host() == null || body.host().isBlank()) {
            throw new IllegalArgumentException("Enter the SMTP server address.");
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("host", body.host().trim());
        settings.put("port", body.port() == null ? 587 : body.port());
        settings.put("security", body.security() == null ? "starttls" : body.security());
        settings.put("username", body.username() == null ? "" : body.username().trim());
        settings.put("authEnabled", body.username() != null && !body.username().isBlank());
        if (body.password() != null && !body.password().isBlank()) {
            settings.put("password", body.password());
        }
        RlsScope.runAsPlatform(() -> smtp.save(settings));
        mailSenders.invalidatePlatform();
        boolean sent = false;
        if (body.testTo() != null && !body.testTo().isBlank()) {
            String to = body.testTo().trim();
            String user = body.username() == null ? "" : body.username().trim();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(user.contains("@") ? user : "noreply@" + body.host().trim());
            message.setTo(to);
            message.setSubject("SecureOne test email");
            message.setText("This is a test email from SecureOne first-run setup. Email delivery works.");
            try {
                RlsScope.asPlatform(() -> mailSenders.getPlatformIfConfigured())
                        .orElseThrow(() -> new IllegalArgumentException("The SMTP settings were not saved."))
                        .send(message);
                sent = true;
            } catch (MailException e) {
                throw new IllegalArgumentException("Saved, but the test email could not be sent: " + rootMessage(e));
            }
        }
        return Map.of("saved", true, "testSent", sent);
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? e.getMessage() : cause.getMessage();
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, String>> complete(
            @RequestBody SetupService.CompleteRequest body, HttpServletRequest request) {
        String email = RlsScope.asPlatform(() -> setup.complete(body, ClientIpResolver.resolve(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("email", email));
    }

    @ExceptionHandler(SetupService.SetupClosedException.class)
    ResponseEntity<Map<String, String>> closed(SetupService.SetupClosedException e) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(SetupService.TooManySetupAttemptsException.class)
    ResponseEntity<Map<String, String>> tooMany(SetupService.TooManySetupAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
    }
}
