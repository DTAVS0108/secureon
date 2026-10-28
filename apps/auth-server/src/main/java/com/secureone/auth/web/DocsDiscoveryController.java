package com.secureone.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public discovery for SecureOne Documentation (UI + API live on admin-web). */
@Tag(name = "Documentation", description = "SecureOne Documentation discovery")
@RestController
@RequestMapping("/api/v1/documentation")
public class DocsDiscoveryController {

    private final String adminWebUrl;

    public DocsDiscoveryController(
            @Value("${secureone.admin-web-url:http://localhost:3001}") String adminWebUrl) {
        this.adminWebUrl = adminWebUrl.endsWith("/") ? adminWebUrl.substring(0, adminWebUrl.length() - 1) : adminWebUrl;
    }

    @Operation(
            summary = "Documentation catalog",
            description =
                    "Lists documentation pages by category and links to the SecureOne Documentation UI (standalone docs browser on admin-web) and session-gated content API. Draw.io diagrams render inline on doc pages.")
    @GetMapping
    public Map<String, Object> catalog() {
        List<Map<String, Object>> categories =
                List.of(
                        category(
                                "getting-started",
                                "Getting started",
                                null,
                                List.of(page("home", "Documentation hub", "README.md"))),
                        category(
                                "platform-fundamentals",
                                "Platform — fundamentals",
                                "Vision, architecture, data & security",
                                List.of(
                                        page("overview", "Overview", "01-overview.md"),
                                        page("tech-stack", "Tech stack", "02-tech-stack.md"),
                                        page("architecture", "Architecture", "03-architecture.md"),
                                        page("data-model", "Data model", "04-data-model.md"),
                                        page("auth-standards", "Auth standards", "05-auth-standards.md"),
                                        page("database", "Database strategy", "06-database.md"),
                                        page(
                                                "database-schemas",
                                                "Schemas & tables",
                                                "17-database-schemas-and-tables.md"),
                                        page("security", "Security", "07-security.md"))),
                        category(
                                "platform-operations",
                                "Platform — operations",
                                "Install, govern, and run",
                                List.of(
                                        page("installation", "Installation", "09-installation.md"),
                                        page(
                                                "environment-setup",
                                                "Environment & operations",
                                                "16-environment-setup-and-operations.md"),
                                        page("enterprise", "Enterprise", "10-enterprise.md"),
                                        page("settings-governance", "Settings governance", "10-settings-governance.md"),
                                        page("admin-control", "Admin control", "11-admin-control.md"),
                                        page("application-rbac", "Application RBAC", "14-application-rbac-management.md"),
                                        page(
                                                "applications-oauth",
                                                "Applications & OAuth clients",
                                                "15-applications-and-oauth-clients.md"),
                                        page(
                                                "verification-otp",
                                                "Verification & OTP",
                                                "19-verification-and-otp.md"))),
                        category(
                                "platform-reference",
                                "Platform — reference",
                                "APIs, ADRs, diagrams",
                                List.of(
                                        page("api-documentation", "API documentation", "12-api-documentation.md"),
                                        page("decisions", "Architecture decisions", "decisions/README.md"),
                                        page("diagrams", "Diagrams", "diagrams/README.md"),
                                        page(
                                                "flyway-migrations",
                                                "Flyway migration files",
                                                "18-flyway-migration-files.md"))),
                        category(
                                "application-integration",
                                "Application integration",
                                "Developers wiring products into SecureOne",
                                List.of(
                                        page("auth-ui-integration", "Auth UI integration", "13-auth-ui-integration.md"),
                                        page(
                                                "verification-otp",
                                                "Verification & OTP",
                                                "19-verification-and-otp.md"),
                                        page(
                                                "application-integration",
                                                "Application integration guide",
                                                "applications/README.md"))));

        List<Map<String, String>> flatPages = new ArrayList<>();
        for (Map<String, Object> category : categories) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> pages = (List<Map<String, String>>) category.get("pages");
            flatPages.addAll(pages);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "SecureOne Documentation");
        out.put("defaultSlug", "home");
        out.put("uiUrl", adminWebUrl + "/documentation");
        out.put("catalogApiUrl", adminWebUrl + "/api/documentation");
        out.put("contentApiPattern", adminWebUrl + "/api/documentation/{slug}");
        out.put("categories", categories);
        out.put("pages", flatPages);
        return out;
    }

    private static Map<String, Object> category(
            String id, String label, String description, List<Map<String, String>> pages) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("label", label);
        if (description != null) {
            c.put("description", description);
        }
        c.put("pages", pages);
        return c;
    }

    private static Map<String, String> page(String slug, String title, String file) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("slug", slug);
        p.put("title", title);
        p.put("file", file);
        p.put("uiPath", "/documentation/" + slug);
        return p;
    }
}
