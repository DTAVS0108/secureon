package com.secureone.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * OAuth 2.0 Protected Resource Metadata (RFC 9728) for SecureOne's own bearer-token APIs (account
 * profile, privacy, UserInfo): which authorization server issues tokens for them and how to present
 * them. The issuer is taken from the request, as the authorization server metadata does.
 */
@Tag(name = "OAuth 2.1 / OIDC")
@RestController
public class ProtectedResourceMetadataController {

    @Operation(summary = "OAuth 2.0 protected resource metadata (RFC 9728)")
    @GetMapping("/.well-known/oauth-protected-resource")
    public ResponseEntity<Map<String, Object>> metadata() {
        String issuer = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resource", issuer);
        out.put("authorization_servers", List.of(issuer));
        out.put("jwks_uri", issuer + "/oauth2/jwks");
        out.put("scopes_supported", List.of("openid", "profile", "email"));
        out.put("bearer_methods_supported", List.of("header"));
        out.put("resource_signing_alg_values_supported", List.of("RS256"));
        out.put("resource_name", "SecureOne account APIs");
        out.put("resource_documentation", issuer + "/docs");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1))).body(out);
    }
}
