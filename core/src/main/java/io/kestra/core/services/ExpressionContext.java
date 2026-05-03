package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * Typed representation of categorized Pebble expressions available for a given context.
 * <p>
 * Used by the {@code POST /flows/expressions} endpoint (No-Code editor autocompletion)
 * and by the AI Copilot prompt builder. Using typed categories instead of raw string keys
 * prevents silent drift when a category is renamed.
 *
 * @param categories ordered map from {@link ExpressionCategory} to sorted expression list
 */
public record ExpressionContext(Map<ExpressionCategory, List<String>> categories) {

    /**
     * Serializes to a stable {@code Map<String, List<String>>} using each category's
     * {@link ExpressionCategory#key()} as the JSON key.
     * Empty categories are omitted.
     * <p>
     * Annotated with {@link JsonValue} so Jackson serializes this record as a flat map
     * (no {@code categories} wrapper) when returned from REST endpoints.
     */
    @JsonValue
    public Map<String, List<String>> toMap() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<ExpressionCategory, List<String>> entry : categories.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                result.put(entry.getKey().key(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Serializes using each category's {@link ExpressionCategory#displayName()} as the key.
     * Suitable for passing to {@link io.kestra.libs.copilot.services.ai.PebbleExpressionsFormatter#format}.
     */
    public SequencedMap<String, List<String>> toDisplayNameMap() {
        SequencedMap<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<ExpressionCategory, List<String>> entry : categories.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                result.put(entry.getKey().displayName(), entry.getValue());
            }
        }
        return result;
    }

    /** Convenience builder. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<ExpressionCategory, List<String>> categories = new LinkedHashMap<>();

        public Builder put(ExpressionCategory category, List<String> expressions) {
            categories.put(category, expressions);
            return this;
        }

        public ExpressionContext build() {
            return new ExpressionContext(Collections.unmodifiableMap(new LinkedHashMap<>(categories)));
        }
    }
}
