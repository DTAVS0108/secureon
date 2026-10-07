package com.secureone.auth.admin;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
        basePackages = {"com.secureone.auth.admin", "com.secureone.auth.account", "com.secureone.auth.application"})
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("UUID")) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "One or more IDs are not valid UUIDs.");
        }
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid request body: " + ex.getMostSpecificCause().getMessage());
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
