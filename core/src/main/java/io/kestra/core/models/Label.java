package io.kestra.core.models;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record Label(@NotNull String key, @NotNull String value) {
    public static final String SYSTEM_PREFIX = "system.";

    // system labels
    public static final String CORRELATION_ID = SYSTEM_PREFIX + "correlationId";
    public static final String USERNAME = SYSTEM_PREFIX + "username";
    public static final String APP = SYSTEM_PREFIX + "app";
    public static final String READ_ONLY = SYSTEM_PREFIX + "readOnly";

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
}
