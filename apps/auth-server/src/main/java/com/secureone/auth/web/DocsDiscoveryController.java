package com.secureone.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public discovery for SecureOne Documentation. The pages, spaces and search live in the console (/admin), which
 * builds them from docs/; the paths are relative so they work on any installation's address.
 */
@Tag(name = "Documentation", description = "SecureOne Documentation discovery")
@RestController
@RequestMapping("/api/v1/documentation")
public class DocsDiscoveryController {

    static final String CONSOLE_PATH = "/admin";

    @Operation(
            summary = "Documentation catalog",
            description = "Where the documentation is: the reader UI, the catalog of spaces and pages, each page's"
                    + " Markdown, and search. Paths are relative to this server. Public pages need no sign-in; pages"
                    + " for operators need a console session.")
    @GetMapping
    public Map<String, Object> catalog() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "SecureOne Documentation");
        out.put("uiPath", CONSOLE_PATH + "/documentation");
        out.put("catalogApiPath", CONSOLE_PATH + "/api/documentation");
        out.put("contentApiPattern", CONSOLE_PATH + "/api/documentation/{slug}");
        out.put("searchApiPath", CONSOLE_PATH + "/api/documentation/search");
        Map<String, String> spaces = new LinkedHashMap<>();
        spaces.put("get-started", "Get started");
        spaces.put("install", "Install and operate");
        spaces.put("integrate", "Integrate applications");
        spaces.put("guide", "Admin console guide");
        spaces.put("api", "API reference");
        spaces.put("architecture", "Architecture and security");
        out.put("spaces", spaces);
        return out;
    }
}
