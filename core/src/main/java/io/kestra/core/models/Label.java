package io.kestra.core.models;

import io.kestra.core.utils.MapUtils;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Label(@NotNull String key, @NotNull String value) {
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

    /**
     * Static helper method for converting a list of labels to a nested map.
     *
     * @param labels The list of {@link Label} to be converted.
     * @return the nested {@link Map}.
     */
    public static Map<String, Object> toNestedMap(List<Label> labels) {
        Map<String, Object> asMap = labels.stream()
            .filter(label -> label.value() != null && label.key() != null)
            // using an accumulator in case labels with the same key exists: the first is kept
            .collect(Collectors.toMap(Label::key, Label::value, (first, second) -> first));
        return MapUtils.flattenToNestedMap(asMap);
    }

    /**
     * Static helper method for converting a map to a list of labels.
     *
     * @param map The map of key/value labels.
     * @return The list of {@link Label labels}.
     */
    public static List<Label> from(final Map<String, String> map) {
        if (map == null || map.isEmpty()) return List.of();
        return map.entrySet()
            .stream()
            .map(entry -> new Label(entry.getKey(), entry.getValue()))
            .toList();
    }

    /**
     * Static helper method for converting a label string to a map.
     *
     * @param label The label string.
     * @return The map of key/value labels.
     */
    public static Map<String, String> from(String label) {
        Map<String, String> map = new HashMap<>();
        String[] keyValueArray = label.split(":");
        if (keyValueArray.length == 2) {
            map.put(keyValueArray[0], keyValueArray[1]);
        }
        return map;
    }
}
