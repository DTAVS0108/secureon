package com.secureone.auth.configascode;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.stream.Collectors;

/** Reads tenant configuration files (YAML or JSON) and writes them, with errors that name the field. */
public final class TenantConfigFormat {

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            // "+10000000000" or "0123" must stay text when read back.
            .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private TenantConfigFormat() {}

    /** Parses a tenant file (YAML or JSON; JSON is valid YAML). Throws IllegalArgumentException with a readable message. */
    public static TenantConfig parse(String text) {
        Object config = parseAny(text);
        if (config instanceof TenantConfig tenant) {
            return tenant;
        }
        throw new IllegalArgumentException("This is a " + PlatformConfig.KIND + " file; a " + TenantConfig.KIND + " was expected.");
    }

    /** A {@link TenantConfig} or a {@link PlatformConfig}, by the file's kind. */
    public static Object parseAny(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The configuration file is empty.");
        }
        try {
            com.fasterxml.jackson.databind.JsonNode tree = YAML.readTree(text);
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                throw new IllegalArgumentException("The configuration file is empty.");
            }
            if (!tree.isObject()) {
                throw new IllegalArgumentException("The file must be a mapping with apiVersion, kind, ...");
            }
            boolean platform = PlatformConfig.KIND.equals(tree.path("kind").asText());
            Class<?> type = platform ? PlatformConfig.class : TenantConfig.class;
            return YAML.treeToValue(tree, type);
        } catch (UnrecognizedPropertyException ex) {
            String envField = ex.getPropertyName() + "Env";
            String hint = ex.getKnownPropertyIds().contains(envField)
                    ? " Credentials never go in the file: put it in an environment variable on the server and name it with "
                            + envField + "."
                    : "";
            throw new IllegalArgumentException(String.format(
                    "Unknown field '%s' at %s%s. Allowed here: %s.%s",
                    ex.getPropertyName(), path(ex), line(ex.getLocation()),
                    ex.getKnownPropertyIds().stream().map(String::valueOf).sorted().collect(Collectors.joining(", ")), hint));
        } catch (MismatchedInputException ex) {
            throw new IllegalArgumentException(String.format(
                    "Wrong value at %s%s: expected %s.",
                    path(ex), line(ex.getLocation()), expected(ex)));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("The file is not valid YAML or JSON" + line(ex.getLocation()) + ": "
                    + ex.getOriginalMessage());
        }
    }

    public static String yaml(TenantConfig config) {
        try {
            return "# SecureOne tenant configuration (docs/install/configuration-and-upgrades.md). Secrets and users are not included.\n"
                    + YAML.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String yaml(PlatformConfig config) {
        try {
            return "# SecureOne platform configuration (docs/install/configuration-and-upgrades.md). Credentials come from environment variables.\n"
                    + YAML.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String json(Object config) {
        try {
            return JSON.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String path(JsonMappingException ex) {
        StringBuilder out = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!out.isEmpty()) {
                    out.append('.');
                }
                out.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                out.append('[').append(ref.getIndex()).append(']');
            }
        }
        return out.isEmpty() ? "the top level" : out.toString();
    }

    private static String expected(MismatchedInputException ex) {
        Class<?> type = ex.getTargetType();
        if (type == null) {
            return "a different value";
        }
        if (java.util.List.class.isAssignableFrom(type)) {
            return "a list";
        }
        if (java.util.Map.class.isAssignableFrom(type) || type.isRecord()) {
            return "a mapping (key: value)";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true or false";
        }
        return "text";
    }

    private static String line(JsonLocation location) {
        return location != null && location.getLineNr() > 0 ? " (line " + location.getLineNr() + ")" : "";
    }
}
