package com.secureone.auth.configascode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuration as code (docs/26). Platform operators only (AdminAccessInterceptor). */
@Tag(name = "Admin — configuration as code", description = "Export a tenant to a file; preview and apply a file")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/config")
public class TenantConfigController {

    private static final int MAX_FILE_CHARS = 2 * 1024 * 1024;
    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    private final TenantConfigService service;
    private final PlatformConfigService platform;

    public TenantConfigController(TenantConfigService service, PlatformConfigService platform) {
        this.service = service;
        this.platform = platform;
    }

    @Operation(summary = "Export the platform configuration", description = "SMTP, SMS and SCIM defaults; YAML or format=json.")
    @GetMapping("/platform/export")
    public ResponseEntity<String> exportPlatform(@RequestParam(defaultValue = "yaml") String format) {
        PlatformConfig config = platform.export();
        boolean json = "json".equalsIgnoreCase(format);
        return file(json ? TenantConfigFormat.json(config) : TenantConfigFormat.yaml(config), "secureone-platform", json);
    }

    @Operation(summary = "Export a tenant's configuration", description = "YAML by default; format=json for JSON.")
    @GetMapping("/tenants/{slug}/export")
    public ResponseEntity<String> export(@PathVariable String slug, @RequestParam(defaultValue = "yaml") String format) {
        TenantConfig config = service.export(slug);
        boolean json = "json".equalsIgnoreCase(format);
        return file(json ? TenantConfigFormat.json(config) : TenantConfigFormat.yaml(config), "secureone-" + slug, json);
    }

    private static ResponseEntity<String> file(String body, String name, boolean json) {
        return ResponseEntity.ok()
                .contentType(new MediaType(json ? MediaType.APPLICATION_JSON : YAML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name + (json ? ".json" : ".yaml"))
                        .build()
                        .toString())
                .body(body);
    }

    @Operation(
            summary = "Preview or apply a configuration file",
            description = "Body: the YAML or JSON file. dryRun=true (default) only reports what would change."
                    + " prune=true also deletes what the file does not list. 422 when the file has problems"
                    + " or applying stopped early.")
    @PostMapping(value = "/import", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<TenantConfigService.Result> importConfig(
            @RequestBody String body,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean prune,
            Authentication authentication) {
        if (body.length() > MAX_FILE_CHARS) {
            throw new IllegalArgumentException("The configuration file is larger than 2 MB.");
        }
        String actor = authentication != null ? authentication.getName() : "admin";
        Object parsed = TenantConfigFormat.parseAny(body);
        TenantConfigService.Result result;
        if (parsed instanceof PlatformConfig config) {
            result = dryRun ? platform.plan(config) : platform.apply(config, actor);
        } else {
            TenantConfig config = (TenantConfig) parsed;
            result = dryRun ? service.plan(config, prune) : service.apply(config, prune, actor);
        }
        return ResponseEntity.status(result.errors().isEmpty() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(result);
    }
}
