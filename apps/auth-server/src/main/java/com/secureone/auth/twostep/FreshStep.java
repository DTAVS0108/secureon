package com.secureone.auth.twostep;

import com.secureone.auth.mfa.MfaPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

/**
 * "Verify it's you": sensitive account changes need a second step done in the last few minutes
 * ({@code freshStepMinutes}), not only a signed-in session that might have been taken over.
 */
@Component
public class FreshStep {

    /** Epoch millis of the session's last second step (sign-in with two steps, or a re-verification). */
    public static final String SESSION_KEY = "secureone.lastSecondStepAt";
    public static final URI PROBLEM_TYPE = URI.create("urn:secureone:fresh-step-required");

    private final MfaPolicyService policy;
    private final TwoStepService twoStep;

    public FreshStep(MfaPolicyService policy, TwoStepService twoStep) {
        this.policy = policy;
        this.twoStep = twoStep;
    }

    public static void mark(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, Instant.now().toEpochMilli());
    }

    public boolean isFresh(HttpServletRequest request, UUID applicationId) {
        HttpSession session = request.getSession(false);
        Object raw = session != null ? session.getAttribute(SESSION_KEY) : null;
        if (!(raw instanceof Number millis)) {
            return false;
        }
        Duration window = Duration.ofMinutes(policy.freshStepMinutes(applicationId));
        return Instant.ofEpochMilli(millis.longValue()).plus(window).isAfter(Instant.now());
    }

    /**
     * Refuses the change unless the user verified recently. Users with no method yet are only asked to be
     * signed in (adding a first method has nothing to verify with).
     */
    public void require(HttpServletRequest request, UUID userId, UUID applicationId) {
        if (twoStep.methods(userId).isEmpty() || isFresh(request, applicationId)) {
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Verify it's you first: confirm with one of your sign-in methods.");
        problem.setType(PROBLEM_TYPE);
        problem.setTitle("Verification required");
        problem.setProperty("freshStepRequired", true);
        throw new ErrorResponseException(HttpStatus.FORBIDDEN, problem, null);
    }
}
