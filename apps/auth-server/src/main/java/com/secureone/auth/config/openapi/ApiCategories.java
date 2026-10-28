package com.secureone.auth.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies {@link ApiCategory} to a generated OpenAPI document: one category's operations, tag order, tag groups. */
final class ApiCategories {

    private ApiCategories() {}

    /** Removes every operation outside the category, then orders what is left. */
    static void keepOnly(OpenAPI openApi, ApiCategory category) {
        if (openApi.getPaths() != null) {
            openApi.getPaths().entrySet().removeIf(entry -> {
                PathItem item = entry.getValue();
                for (Map.Entry<PathItem.HttpMethod, Operation> op : new ArrayList<>(item.readOperationsMap().entrySet())) {
                    if (!belongsTo(op.getValue(), category)) {
                        item.operation(op.getKey(), null);
                    }
                }
                return item.readOperations().isEmpty();
            });
        }
        orderTags(openApi, category);
    }

    /**
     * Lists the tags in use in reading order (keeping their descriptions) and, for the whole API ({@code category}
     * null), groups them by category in {@code x-tagGroups}.
     */
    static void orderTags(OpenAPI openApi, ApiCategory category) {
        Set<String> used = new LinkedHashSet<>();
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(op -> {
                if (op.getTags() != null) {
                    used.addAll(op.getTags());
                }
            }));
        }
        Map<String, Tag> known = new LinkedHashMap<>();
        if (openApi.getTags() != null) {
            openApi.getTags().forEach(tag -> known.put(tag.getName(), tag));
        }
        List<Tag> tags = used.stream()
                .sorted(Comparator.comparingInt(ApiCategory::order).thenComparing(Comparator.naturalOrder()))
                .map(name -> known.getOrDefault(name, new Tag().name(name)))
                .toList();
        openApi.setTags(new ArrayList<>(tags));

        if (category == null) {
            List<Map<String, Object>> groups = new ArrayList<>();
            for (ApiCategory c : ApiCategory.values()) {
                List<String> names = c.tags().stream().filter(used::contains).toList();
                if (!names.isEmpty()) {
                    Map<String, Object> group = group(c.title(), names);
                    group.put("x-id", c.id()); // its own definition: /v3/api-docs/{x-id}
                    groups.add(group);
                }
            }
            List<String> other = used.stream().filter(name -> ApiCategory.of(name).isEmpty()).sorted().toList();
            if (!other.isEmpty()) {
                groups.add(group("Other", other));
            }
            openApi.addExtension("x-tagGroups", groups);
        }
    }

    private static boolean belongsTo(Operation operation, ApiCategory category) {
        return operation.getTags() != null && operation.getTags().stream().anyMatch(category.tags()::contains);
    }

    private static Map<String, Object> group(String name, List<String> tags) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", name);
        group.put("tags", tags);
        return group;
    }
}
