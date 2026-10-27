package com.secureone.auth.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints for custom domains: proof that a domain points here, and the reverse proxy's TLS check. */
@Tag(name = "Custom domains", description = "Domain verification and the on-demand TLS check")
@RestController
public class DomainVerificationController {

    private final TenantDomainService domains;

    public DomainVerificationController(TenantDomainService domains) {
        this.domains = domains;
    }

    @Operation(summary = "On a registered domain: its verification token (shows the domain points at SecureOne)")
    @GetMapping(value = TenantDomainService.WELL_KNOWN, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> token(HttpServletRequest request) {
        return domains.tokenFor(request.getServerName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Whether a domain is a verified custom domain (200) or not (404)",
            description = "For a reverse proxy that issues certificates on demand (Caddy on_demand_tls ask): it gets a certificate only for verified domains.")
    @GetMapping("/api/v1/domains/check")
    public ResponseEntity<Void> check(@RequestParam("domain") String domain) {
        return domains.isVerified(domain) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
