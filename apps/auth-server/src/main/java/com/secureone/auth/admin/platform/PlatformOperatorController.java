package com.secureone.auth.admin.platform;

import com.secureone.auth.admin.AdminAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — platform operators", description = "Named accounts that hold the platform super admin role")
@RestController
@RequestMapping("/api/admin/v1/platform-operators")
public class PlatformOperatorController {

    private final PlatformOperatorService service;
    private final AdminAccessService access;

    public PlatformOperatorController(PlatformOperatorService service, AdminAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public List<PlatformOperators.Row> list(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformOperators.Row add(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody PlatformOperatorService.AddOperatorRequest body) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        return service.add(body, authentication.getName());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID userId) {
        access.requirePlatformSettingsAccess(authentication, actAsEmail);
        service.remove(userId, authentication.getName(), access.resolveOperatorEmail(authentication, null));
    }
}
