package io.kestra.core.models.triggers;

import java.util.List;
import java.util.Map;

import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.validations.NoSystemLabelValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Plugin
@SuperBuilder
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract public class AbstractTrigger implements TriggerInterface {
    protected String id;

    protected String type;

    @PluginProperty(hidden = true, group = "advanced")
    protected String version;

    @PluginProperty(hidden = true, group = "advanced")
    private String description;

    @PluginProperty(group = "reliability")
    @Schema(
        title = "List of conditions in order to limit the flow trigger.",
        description = "**DEPRECATED**, use `when` instead."
    )
    @Valid
    @Deprecated(forRemoval = true, since = "2.0.0")
    protected List<@Valid @NotNull Condition> conditions;

    @Builder.Default
    @NotNull
    @PluginProperty(group = "execution", dynamic = true)
    @Schema(
        title = "A condition that determines whether the trigger should run.",
        description = "A Pebble expression evaluated at trigger time. The trigger fires only when the expression evaluates to a truthy value (`true`, a non-empty string, a non-zero number). Use this to gate trigger execution on dynamic runtime values such as execution labels, flow variables, or environment conditions."
    )
    private String when = "true";

    @Builder.Default
    @PluginProperty(hidden = true, group = "execution")
    @Schema(defaultValue = "false")
    private boolean disabled = false;

    @Valid
    @PluginProperty(hidden = true, group = "execution")
    private WorkerGroup workerGroup;

    @PluginProperty(hidden = true, group = "logging")
    private Level logLevel;

    @Schema(
        title = "The labels to pass to the execution created.",
        implementation = Object.class, oneOf = { List.class, Map.class }
    )
    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @PluginProperty(hidden = true, group = "advanced")
    private List<@NoSystemLabelValidation Label> labels;

    @PluginProperty(group = "reliability")
    @Schema(
        title = "List of execution states after which a trigger should be stopped (a.k.a. disabled)."
    )
    private List<State.Type> stopAfter;

    @Builder.Default
    @PluginProperty(hidden = true, group = "logging")
    private boolean logToFile = false;

    @Builder.Default
    @PluginProperty(hidden = true, group = "reliability")
    private boolean failOnTriggerError = false;

    @Builder.Default
    @PluginProperty(group = "execution")
    @Schema(
        title = "Specifies whether a trigger is allowed to start a new execution even if a previous run is still in progress."
    )
    private boolean allowConcurrent = false;

    @PluginProperty(hidden = true, group = "advanced")
    private AssetsDeclaration assets;
}
