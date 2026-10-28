package com.secureone.auth.config.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class ApiCategoryTest {

    /** Every @Tag on a controller (class or method) belongs to exactly one category. */
    @Test
    void everyControllerTagHasOneCategory() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(io.swagger.v3.oas.annotations.tags.Tag.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Controller.class));
        Set<String> tags = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.secureone.auth")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            for (var tag : type.getAnnotationsByType(io.swagger.v3.oas.annotations.tags.Tag.class)) {
                tags.add(tag.name());
            }
            for (Method method : type.getDeclaredMethods()) {
                for (var tag : method.getAnnotationsByType(io.swagger.v3.oas.annotations.tags.Tag.class)) {
                    tags.add(tag.name());
                }
            }
        }
        tags.add("OAuth 2.1 / OIDC"); // OAuth2OpenApiDocumentation (protocol endpoints, not controllers)
        assertTrue(tags.size() > 40, "scan found the controllers: " + tags);

        List<String> missing = tags.stream().filter(t -> ApiCategory.of(t).isEmpty()).toList();
        assertEquals(List.of(), missing, "add these tags to an ApiCategory");

        List<String> all = Arrays.stream(ApiCategory.values()).flatMap(c -> c.tags().stream()).toList();
        assertEquals(all.size(), new HashSet<>(all).size(), "a tag is listed in two categories");
        List<String> unused = all.stream().filter(t -> !tags.contains(t)).toList();
        assertEquals(List.of(), unused, "categories list tags no controller uses");
    }

    @Test
    void categoryIdsAreUniqueAndUrlSafe() {
        List<String> ids = Arrays.stream(ApiCategory.values()).map(ApiCategory::id).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size());
        ids.forEach(id -> assertTrue(id.matches("[a-z][a-z-]*"), id));
        assertTrue(!ids.contains("all"), "\"all\" is the whole API");
    }

    @Test
    void aCategoryKeepsOnlyItsOperationsInReadingOrder() {
        OpenAPI api = api();
        ApiCategories.keepOnly(api, ApiCategory.PUBLIC);

        assertEquals(Set.of("/api/info", "/api/v1/setup"), api.getPaths().keySet());
        assertEquals(List.of("System", "First-run setup"), api.getTags().stream().map(Tag::getName).toList());
        assertEquals("Server info", api.getTags().getFirst().getDescription(), "descriptions are kept");
    }

    @Test
    void mixedPathsLoseOnlyTheOtherCategoriesOperations() {
        OpenAPI api = api();
        ApiCategories.keepOnly(api, ApiCategory.ADMIN_SETTINGS);

        PathItem settings = api.getPaths().get("/api/admin/v1/settings/attack-protection");
        assertEquals(2, settings.readOperations().size());
        assertEquals(Set.of("/api/admin/v1/settings/attack-protection"), api.getPaths().keySet());
    }

    @Test
    void theWholeApiIsOrderedAndGroupedByCategory() {
        OpenAPI api = api();
        ApiCategories.orderTags(api, null);

        assertEquals(List.of("System", "First-run setup", "Public auth", "OAuth 2.1 / OIDC", "Admin — attack protection",
                "Unlisted"), api.getTags().stream().map(Tag::getName).toList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) api.getExtensions().get("x-tagGroups");
        assertEquals(List.of("Public", "Authentication", "OAuth 2.1 / OIDC and SAML", "Admin — platform settings", "Other"),
                groups.stream().map(g -> g.get("name")).toList());
        assertEquals(List.of("System", "First-run setup"), groups.getFirst().get("tags"));
    }

    private static OpenAPI api() {
        Paths paths = new Paths();
        paths.addPathItem("/api/admin/v1/settings/attack-protection", new PathItem()
                .get(op("Admin — attack protection")).put(op("Admin — attack protection")));
        paths.addPathItem("/oauth2/token", new PathItem().post(op("OAuth 2.1 / OIDC")));
        paths.addPathItem("/api/v1/auth/login", new PathItem().post(op("Public auth")));
        paths.addPathItem("/api/v1/setup", new PathItem().get(op("First-run setup")));
        paths.addPathItem("/api/info", new PathItem().get(op("System")));
        paths.addPathItem("/api/v1/other", new PathItem().get(op("Unlisted")));
        OpenAPI api = new OpenAPI().paths(paths);
        api.setTags(new ArrayList<>(List.of(new Tag().name("System").description("Server info"))));
        return api;
    }

    private static Operation op(String tag) {
        return new Operation().tags(new ArrayList<>(List.of(tag)));
    }
}
