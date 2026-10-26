package com.secureone.auth.admin;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
        basePackages = {
            "com.secureone.auth.admin",
            "com.secureone.auth.account",
            "com.secureone.auth.application",
            "com.secureone.auth.configascode",
            "com.secureone.auth.mfa",
            "com.secureone.auth.protection",
            "com.secureone.auth.audit",
            "com.secureone.auth.twostep"
        })
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Keeps the status and reason of explicit ResponseStatusExceptions (403, 429, ...) in the problem body. */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ProblemDetail responseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    }

    /**
     * Problems thrown with their own body (for example "verify it's you first", whose {@code freshStepRequired}
     * flag tells the console and the account page to ask for the second step) keep it.
     */
    @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
    org.springframework.http.ResponseEntity<ProblemDetail> errorResponse(org.springframework.web.ErrorResponseException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail serviceUnavailable(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * A bare {@code Optional.orElseThrow()} on a lookup. With row-level security, a row in another
     * tenant is simply absent, so this is "not found" rather than a server error.
     */
    @ExceptionHandler(java.util.NoSuchElementException.class)
    ProblemDetail notFoundLookup(java.util.NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Another request changed or removed the same record between this request loading and saving it,
     * e.g. two admins deleting the same user. The caller should reload, not see a server error.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentChange(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This record was changed or removed by another request. Refresh and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail == null) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Database constraint violation.");
        }
        String lower = detail.toLowerCase();
        if (lower.contains("unique") || lower.contains("duplicate key")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "A record with the same unique key already exists.");
        }
        if (lower.contains("foreign key") && lower.contains("email_token")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Could not complete sign-up notification setup. Please try again.");
        }
        if (lower.contains("(tenant_id, email)")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists. Try signing in or reset your password.");
        }
        if (lower.contains("application_tenant_id_slug_key") || lower.contains("(tenant_id, slug)")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "An OAuth client with this ID already exists for this tenant.");
        }
        if (lower.contains("role_application_id_name_key") || lower.contains("(application_id, name)")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Default roles could not be seeded for this client. Try again.");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Database constraint violation.");
    }

    private static final java.util.regex.Pattern FIELD_IN_REFERENCE_CHAIN =
            java.util.regex.Pattern.compile("\\[\"([^\"]+)\"\\]");

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("UUID")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "One or more IDs are not valid UUIDs.");
        }
        // Name the field when the parser says which one, never echo parser internals (class names,
        // DeserializationFeature hints) to API clients.
        if (msg != null) {
            // Jackson's reference chain ends with the offending field: ...Foo["isComposite"]
            java.util.regex.Matcher all = FIELD_IN_REFERENCE_CHAIN.matcher(msg);
            String last = null;
            while (all.find()) {
                last = all.group(1);
            }
            if (last != null) {
                return ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Invalid or missing value for '%s'.".formatted(last));
            }
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '%s'.".formatted(ex.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setDetail("Validation failed");
        problem.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                        .toList());
        return problem;
    }
}
