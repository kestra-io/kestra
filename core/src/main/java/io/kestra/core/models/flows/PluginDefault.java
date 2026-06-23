package io.kestra.core.models.flows;

import java.util.Map;

import io.kestra.core.validations.PluginDefaultValidation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@Introspected
@PluginDefaultValidation
public class PluginDefault {
    @NotNull
    private final String type;

    @Builder.Default
    private final boolean forced = false;

    @Schema(
        title = "Optional reference id used to apply this default only to plugins that opt in via `pluginDefaultsRef`."
    )
    private final String ref;

    @Schema(
        type = "object",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    private final Map<String, Object> values;

    // explicit @Creator so bean introspection (Micronaut jackson-databind) deserializes through the full
    // constructor — with the convenience constructor below also present, the creator must be unambiguous or
    // the 'ref' property fails to map ("Invalid json mapping") on namespace plugin-defaults payloads.
    @Creator
    @JsonCreator
    public PluginDefault(String type, boolean forced, String ref, Map<String, Object> values) {
        this.type = type;
        this.forced = forced;
        this.ref = ref;
        this.values = values;
    }

    /**
     * Convenience constructor for type-matched (non-{@code ref}) defaults.
     */
    public PluginDefault(String type, boolean forced, Map<String, Object> values) {
        this(type, forced, null, values);
    }
}
