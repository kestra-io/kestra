package io.kestra.core.models.triggers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
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
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;

@Plugin
@SuperBuilder
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract public class AbstractTrigger implements TriggerInterface {
    protected String id;

    protected String type;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    protected String version;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private String description;

    @PluginProperty(group = PluginProperty.CORE_GROUP)
    @Schema(
        title = "List of conditions in order to limit the flow trigger."
    )
    @Valid
    protected List<@Valid @NotNull Condition> conditions;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    @Schema(defaultValue = "false")
    private boolean disabled = false;

    @Valid
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private WorkerGroup workerGroup;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private Level logLevel;

    @Schema(
        title = "The labels to pass to the execution created.",
        implementation = Object.class, oneOf = {List.class, Map.class}
    )
    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private List<@NoSystemLabelValidation Label> labels;

    @PluginProperty(group = PluginProperty.CORE_GROUP)
    @Schema(
        title = "List of execution states after which a trigger should be stopped (a.k.a. disabled)."
    )
    private List<State.Type> stopAfter;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private boolean logToFile = false;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private boolean failOnTriggerError = false;

    @PluginProperty(group = PluginProperty.CORE_GROUP)
    @Schema(
        title = "Specifies whether a trigger is allowed to start a new execution even if a previous run is still in progress."
    )
    private boolean allowConcurrent = false;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private Property<AssetsDeclaration> assets;

    /**
     * For backward compatibility: we rename minLogLevel to logLevel.
     * @deprecated use {@link #logLevel} instead
     */
    @Deprecated
    public void setMinLogLevel(Level minLogLevel) {
        this.logLevel = minLogLevel;
    }
}
