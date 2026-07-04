package com.secureone.auth.authn;

import com.secureone.auth.admin.impersonation.AdminImpersonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public auth — impersonation", description = "Consume admin impersonation tokens")
@RestController
@RequestMapping("/api/v1/auth/impersonation")
public class ImpersonationAuthController {

    private final AdminImpersonationService impersonation;

    public ImpersonationAuthController(AdminImpersonationService impersonation) {
        this.impersonation = impersonation;
    }

    @Operation(summary = "Consume impersonation token and establish a user session")
    @GetMapping("/consume")
    public void consume(
            @RequestParam String token,
            @RequestParam(required = false) String continueUrl,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        impersonation.consumeToken(token, request, response);
        String redirect = "/login.html?signedIn=1&impersonated=1";
        if (continueUrl != null && !continueUrl.isBlank()) {
            redirect += "&continue="
                    + URLEncoder.encode(continueUrl.trim(), StandardCharsets.UTF_8);
        }
        response.sendRedirect(redirect);
    }
}
