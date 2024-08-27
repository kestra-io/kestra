package io.kestra.core.models.triggers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.util.List;

@Plugin
@SuperBuilder
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract public class AbstractTrigger implements TriggerInterface {
    protected String id;

    protected String type;

    private String description;

    @Valid
    @PluginProperty
    @Schema(
        title = "List of conditions in order to limit the flow trigger."
    )
    protected List<Condition> conditions;

    @NotNull
    @Builder.Default
    private boolean disabled = false;

    @Valid
    private WorkerGroup workerGroup;

    private Level logLevel;

    @Schema(
        title = "The labels to pass to the execution created."
    )
    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    private List<Label> labels;

    @PluginProperty
    @Schema(
        title = "List of execution states after which a trigger should be stopped (a.k.a. disabled)."
    )
    private List<State.Type> stopAfter;

    @Builder.Default
    private boolean logToFile = false;

    /**
     * For backward compatibility: we rename minLogLevel to logLevel.
     * @deprecated use {@link #logLevel} instead
     */
    @Deprecated
    public void setMinLogLevel(Level minLogLevel) {
        this.logLevel = minLogLevel;
    }
}
