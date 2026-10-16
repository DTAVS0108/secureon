package com.secureone.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicManifestDefaultsTest {

    @Test
    void sectionMissingFromStoredPlatformValueFallsBackToCodeDefault() {
        // A platform row saved before "federation" and "verificationPolicy" existed.
        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("enabled", false);
        platform.put("sections", Map.of("application", true, "appearance", true));

        Map<String, Object> merged = PublicManifestDefaults.merge(platform, Map.of());

        Map<?, ?> sections = (Map<?, ?>) merged.get("sections");
        assertEquals(true, sections.get("federation"));
        assertEquals(true, sections.get("verificationPolicy"));
        assertEquals(true, sections.get("appearance"), "stored platform value wins over the code default");
    }

    @Test
    void appOverrideWinsPerSection() {
        Map<String, Object> app = Map.of("enabled", true, "sections", Map.of("federation", false));

        Map<String, Object> merged = PublicManifestDefaults.merge(null, app);

        Map<?, ?> sections = (Map<?, ?>) merged.get("sections");
        assertEquals(true, merged.get("enabled"));
        assertEquals(false, sections.get("federation"));
        assertEquals(true, sections.get("application"));
    }

    @Test
    void mergeDoesNotMutateItsInputs() {
        Map<String, Object> platformSections = new LinkedHashMap<>(Map.of("application", true));
        Map<String, Object> platform = new LinkedHashMap<>(Map.of("sections", platformSections));

        PublicManifestDefaults.merge(platform, Map.of("sections", Map.of("application", false)));

        assertEquals(Map.of("application", true), platformSections);
    }
}
