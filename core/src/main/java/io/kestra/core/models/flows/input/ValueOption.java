package io.kestra.core.models.flows.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single option in a {@link SelectInput} or {@link MultiselectInput} list of values.
 * <p>
 * Supports two YAML/JSON forms for backwards-compatibility:
 * <ul>
 *     <li>A plain scalar (e.g. {@code "V1"}) — label and value are both set to that scalar.</li>
 *     <li>An object {@code {label: "Production", value: "123"}} — label and value are decoupled.</li>
 * </ul>
 * Serialization mirrors the input: a {@link ValueOption} whose label equals its value is emitted as a plain string,
 * otherwise as an object — preserving the original schema for existing flows.
 */
@Schema(
    description = "A select option. Accepts either a plain string (used as both label and value) or an object with `label` and `value` fields.",
    anyOf = {String.class, ValueOption.ValueOptionObject.class}
)
public record ValueOption(@NotNull String label, @NotNull String value) {

    @JsonCreator
    public static ValueOption from(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ValueOption v) {
            return v;
        }
        if (raw instanceof Map<?, ?> map) {
            Object rawValue = map.get("value");
            if (rawValue == null) {
                throw new IllegalArgumentException("Select option object must define a `value` field");
            }
            Object rawLabel = map.containsKey("label") ? map.get("label") : rawValue;
            return new ValueOption(rawLabel.toString(), rawValue.toString());
        }
        String str = raw.toString();
        return new ValueOption(str, str);
    }

    @JsonValue
    public Object toJson() {
        if (Objects.equals(label, value)) {
            return value;
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("value", value);
        return map;
    }

    /** Schema-only helper to document the object form of a {@link ValueOption}. */
    @Schema(name = "ValueOption", description = "Object form of a select option.")
    record ValueOptionObject(@NotNull String label, @NotNull String value) {}
}
