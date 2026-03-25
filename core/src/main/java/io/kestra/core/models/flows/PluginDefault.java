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
public class PluginDefault {
    @NotNull
    private final String type;

    @Builder.Default
    private final boolean forced = false;

    @Schema(
        type = "object",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    private final Map<String, Object> values;
}
