package com.secureone.auth.admin.iga;

import com.secureone.auth.admin.AdminOperatorService;
import com.secureone.auth.iga.AccessRequestService;
import com.secureone.auth.user.UserAccount;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — access requests", description = "Identity governance access request workflow (stub)")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/access-requests")
public class AccessRequestAdminController {

    private final AccessRequestService service;
    private final AdminOperatorService operators;

    public AccessRequestAdminController(AccessRequestService service, AdminOperatorService operators) {
        this.service = service;
        this.operators = operators;
    }

    /**
     * The reviewer is the signed-in operator. Only the platform operator, who has no tenant user
     * account, may record a reviewer explicitly.
     */
    private UUID reviewer(Authentication authentication, String actAsEmail, UUID requested) {
        return operators.resolveTenantUser(authentication, actAsEmail)
                .map(UserAccount::getId)
                .orElse(requested);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID tenantId) {
        return service.list(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return service.create(tenantId, body);
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID reviewerId) {
        return service.approve(tenantId, id, reviewer(authentication, actAsEmail, reviewerId));
    }

    @PostMapping("/{id}/deny")
    public Map<String, Object> deny(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID reviewerId) {
        return service.deny(tenantId, id, reviewer(authentication, actAsEmail, reviewerId));
    }
}
