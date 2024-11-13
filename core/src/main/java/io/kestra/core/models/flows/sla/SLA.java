package io.kestra.core.models.flows.sla;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.sla.types.ExecutionConditionSLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.Optional;

@SuperBuilder
@Getter
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = MaxDurationSLA.class, name = "MAX_DURATION"),
    @JsonSubTypes.Type(value = ExecutionConditionSLA.class, name = "EXECUTION_CONDITION"),
})
public abstract class SLA {
    @NotNull
    @NotEmpty
    private String id;

    @NotNull
    private SLA.Type type;

    @NotNull
    private Behavior behavior;

    // TODO prevent system labels
    private Map<String, Object> labels;

    /**
     * Evaluate a flow SLA on an execution.
     * In case the SLA is exceeded, a violation will be returned.
     */
    public abstract Optional<Violation> evaluate(RunContext runContext, Execution execution) throws InternalException;

    public enum Type {
        MAX_DURATION,
        EXECUTION_CONDITION,
    }

    public enum Behavior {
        FAIL,
        CANCEL,
        NONE
    }
}
