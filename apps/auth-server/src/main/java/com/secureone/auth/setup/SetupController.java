package com.secureone.auth.setup;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.secureone.auth.authn.DevAdminAuthenticationProvider;
import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public SetupController(SetupService setup, ObjectProvider<DevAdminAuthenticationProvider> emergencyAccount) {
        this.setup = setup;
        this.emergencyAccount = emergencyAccount;
    }

    /** {@code emergencyAccess}: whether the console sign-in page offers the break-glass account. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean required = setup.setupRequired();
        return Map.of(
                "setupRequired", required,
                "codeFromEnvironment", required && setup.codeFromEnvironment(),
                "emergencyAccess", emergencyAccount.getIfAvailable() != null);
    }

    public record VerifyRequest(String code) {}

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@RequestBody VerifyRequest body, HttpServletRequest request) {
        setup.verify(body == null ? null : body.code(), ClientIpResolver.resolve(request));
        return ResponseEntity.noContent().build();
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
