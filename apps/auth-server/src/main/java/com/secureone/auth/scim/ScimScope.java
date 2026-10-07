package com.secureone.auth.scim;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Which tenant a SCIM request may touch. A tenant token binds the request to its tenant; the legacy
 * platform token leaves it unbound and the caller must name the tenant.
 */
final class ScimScope {

    static final String TENANT_ATTRIBUTE = ScimScope.class.getName() + ".tenantId";

    private ScimScope() {}

    static UUID boundTenant(HttpServletRequest request) {
        return (UUID) request.getAttribute(TENANT_ATTRIBUTE);
    }

    /** Tenant for list/create: the token's tenant, or (platform token) the one the caller named. */
    static UUID resolve(HttpServletRequest request, UUID requested) {
        UUID bound = boundTenant(request);
        if (bound != null) {
            if (requested != null && !requested.equals(bound)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This SCIM token cannot access another tenant.");
            }
            return bound;
        }
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tenantId is required with the platform SCIM token; use a tenant SCIM token instead.");
        }
        return requested;
    }

    /** Records outside the token's tenant are reported as not found. */
    static void requireVisible(HttpServletRequest request, UUID recordTenantId) {
        UUID bound = boundTenant(request);
        if (bound != null && !bound.equals(recordTenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
