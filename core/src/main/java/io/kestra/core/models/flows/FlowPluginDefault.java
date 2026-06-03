package io.kestra.core.models.flows;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.validations.PluginDefaultValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A plugin default entry scoped to a single flow.
 * <p>
 * The {@code forced} flag is always ignored at flow level (for type-matched and named ({@code ref})
 * entries alike): only administrators can enforce plugin defaults, via namespace, tenant, or global
 * configuration (see {@code PluginDefaultService#getFlowDefaults}).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@PluginDefaultValidation
public class FlowPluginDefault implements PluginDefaultSpec {
    @NotNull
    private String type;

    @Builder.Default
    private boolean forced = false;

    @Schema(
        title = "Optional reference id used to apply this default only to plugins that opt in via `pluginDefaultsRef`."
    )
    private String ref;

    @Schema(
        type = "object",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    private Map<String, Object> values;
}
