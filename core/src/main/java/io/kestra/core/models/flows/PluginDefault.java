package io.kestra.core.models.flows;

import java.util.Map;

import io.kestra.core.validations.PluginDefaultValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@PluginDefaultValidation
public class PluginDefault implements PluginDefaultSpec {
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

    /**
     * Convenience constructor for type-matched (non-{@code ref}) defaults.
     */
    public PluginDefault(String type, boolean forced, Map<String, Object> values) {
        this(type, forced, null, values);
    }
}
