package com.secureone.auth.admin;

import com.secureone.auth.mfa.MfaPolicyService;
import com.secureone.auth.twostep.FreshStep;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

/**
 * "Verify it's you" for the admin console: approving changes, resetting or exempting someone's two-step
 * verification, new client secrets or API keys need a second step from the last few minutes
 * (platform {@code freshStepMinutes}), not only a console session. Signing in counts; so does
 * {@code POST /api/admin/v1/me/step-up}.
 */
@Service
public class AdminStepUpService {

    /** Request attribute with the console session token (set by the credential filter). */
    public static final String TOKEN_ATTR = AdminStepUpService.class.getName() + ".token";
    private static final String PREFIX = "secureone:admin-stepup:";

    private final StringRedisTemplate redis;
    private final MfaPolicyService policy;
    private final AdminAccessService access;

    public AdminStepUpService(StringRedisTemplate redis, MfaPolicyService policy, AdminAccessService access) {
        this.redis = redis;
        this.policy = policy;
        this.access = access;
    }

    public void mark(String sessionToken) {
        if (sessionToken != null) {
            redis.opsForValue().set(PREFIX + hash(sessionToken), String.valueOf(Instant.now().toEpochMilli()), Duration.ofHours(9));
        }
    }

    public void mark(HttpServletRequest request) {
        mark((String) request.getAttribute(TOKEN_ATTR));
    }

    public boolean isFresh(HttpServletRequest request) {
        String token = (String) request.getAttribute(TOKEN_ATTR);
        if (token == null) {
            return false;
        }
        String raw = redis.opsForValue().get(PREFIX + hash(token));
        if (raw == null) {
            return false;
        }
        Duration window = Duration.ofMinutes(policy.freshStepMinutes(null));
        return Instant.ofEpochMilli(Long.parseLong(raw)).plus(window).isAfter(Instant.now());
    }

    /** Refuses the action unless the operator verified recently (the emergency account has no second step). */
    public void require(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (access.isBreakGlass(auth) || isFresh(request)) {
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Verify it's you first: enter a code from your authenticator app.");
        problem.setType(FreshStep.PROBLEM_TYPE);
        problem.setTitle("Verification required");
        problem.setProperty("freshStepRequired", true);
        throw new ErrorResponseException(HttpStatus.FORBIDDEN, problem, null);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
