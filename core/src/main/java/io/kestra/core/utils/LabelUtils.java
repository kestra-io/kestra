package io.kestra.core.utils;

import io.kestra.core.models.Label;

import java.util.List;
import java.util.Map;

public final class LabelUtils {
    private LabelUtils() {
        //utility class pattern
    }

    public static List<Label> from(Map<String, String> labels) {
        return labels == null ? null : labels.entrySet().stream()
            .map(entry -> new Label(entry.getKey(), entry.getValue()))
            .toList();
    }
}
