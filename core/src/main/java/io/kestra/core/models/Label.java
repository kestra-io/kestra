package io.kestra.core.models;

import io.kestra.core.utils.MapUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.*;
import java.util.stream.Collectors;

public record Label(
    @NotNull
    @Pattern(
        regexp = "^[a-z][a-z0-9_-]*$",
        message = "Label keys must start with a lowercase letter and contain only lowercase letters, numbers, hyphens, or underscores."
    )
    String key,

    @NotNull
    String value
) {
    public static final String SYSTEM_PREFIX = "system.";

    // system labels
    public static final String CORRELATION_ID = SYSTEM_PREFIX + "correlationId";
    public static final String USERNAME = SYSTEM_PREFIX + "username";
    public static final String APP = SYSTEM_PREFIX + "app";
    public static final String READ_ONLY = SYSTEM_PREFIX + "readOnly";
    public static final String RESTARTED = SYSTEM_PREFIX + "restarted";
    public static final String REPLAY = SYSTEM_PREFIX + "replay";
    public static final String REPLAYED = SYSTEM_PREFIX + "replayed";
    public static final String SIMULATED_EXECUTION = SYSTEM_PREFIX + "simulatedExecution";
    public static final String TEST = SYSTEM_PREFIX + "test";

    // --- static helper methods stay the same ---
    public static Map<String, Object> toNestedMap(List<Label> labels) {
        return MapUtils.flattenToNestedMap(toMap(labels));
    }

    public static Map<String, String> toMap(@Nullable List<Label> labels) {
        if (labels == null || labels.isEmpty()) return Collections.emptyMap();
        return labels.stream()
            .filter(label -> label.value() != null && label.key() != null)
            .collect(Collectors.toMap(Label::key, Label::value, (first, second) -> second, LinkedHashMap::new));
    }

    public static List<Label> deduplicate(@Nullable List<Label> labels) {
        if (labels == null || labels.isEmpty()) return Collections.emptyList();
        return toMap(labels).entrySet().stream()
            .map(entry -> new Label(entry.getKey(), entry.getValue()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<Label> from(final Map<String, String> map) {
        if (map == null || map.isEmpty()) return List.of();
        return map.entrySet()
            .stream()
            .map(entry -> new Label(entry.getKey(), entry.getValue()))
            .toList();
    }

    public static Map<String, String> from(String label) {
        Map<String, String> map = new HashMap<>();
        String[] keyValueArray = label.split(":");
        if (keyValueArray.length == 2) {
            map.put(keyValueArray[0], keyValueArray[1]);
        }
        return map;
    }
}
