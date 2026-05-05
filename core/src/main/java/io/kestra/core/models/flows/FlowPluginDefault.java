package io.kestra.core.models.flows;

import java.util.Map;

import io.kestra.core.validations.PluginDefaultValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A plugin default entry scoped to a single flow.
 * The {@code forced} flag is intentionally absent: flow-level defaults cannot override
 * values enforced at namespace or tenant level by administrators.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@PluginDefaultValidation
public class FlowPluginDefault implements PluginDefaultSpec {
    @NotNull
    private String type;

    @Schema(
        type = "object",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    private Map<String, Object> values;
}
