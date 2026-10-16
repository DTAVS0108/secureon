package com.secureone.auth.web;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight public info endpoint (handy for smoke checks and uptime probes). */
@Tag(name = "System", description = "Health and discovery")
@RestController
@RequestMapping("/api")
public class InfoController {

    private final String adminWebUrl;
    private final SecureOneEnvironmentProperties environment;
    private final String version;

    public InfoController(
            @Value("${secureone.admin-web-url:http://localhost:3001}") String adminWebUrl,
            SecureOneEnvironmentProperties environment,
            ObjectProvider<BuildProperties> build) {
        this.adminWebUrl = adminWebUrl.endsWith("/") ? adminWebUrl.substring(0, adminWebUrl.length() - 1) : adminWebUrl;
        this.environment = environment;
        BuildProperties properties = build.getIfAvailable();
        this.version = properties != null ? properties.getVersion() : "development";
    }

    @Operation(summary = "Service info", description = "Public metadata including links to OpenAPI documentation.")
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "SecureOne Auth Server");
        out.put("status", "UP");
        out.put("version", version);
        out.put("environment", environment.getEnvironment());
        out.put("features", featureFlags());
        out.put("openapi", "/v3/api-docs");
        out.put("swaggerUi", "/swagger-ui/index.html");
        out.put("docs", "/docs");
        out.put("confluence", adminWebUrl + "/confluence");
        out.put("confluenceApi", adminWebUrl + "/api/confluence");
        out.put("confluenceDiscovery", "/api/v1/confluence");
        return out;
    }

    private Map<String, Object> featureFlags() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("swagger", environment.isSwaggerEnabled());
        features.put("devAuth", environment.isDevAuthEnabled());
        return features;
    }
}
