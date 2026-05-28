package io.kestra.core.models.triggers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

/**
 * Lightweight representation of a trigger evaluation result.
 * <p>
 * This record carries only the data needed to reconstruct a full {@link Execution}
 * on the controller/scheduler side, avoiding the overhead of transporting
 * the full Execution object over gRPC when only a subset is populated.
 * <p>
 * Flow-level variables are NOT transported — the executor resolves them
 * directly from the {@code Flow} via {@code RunVariables}.
 *
 * @param executionId  the generated execution ID.
 * @param stateType    the execution state type (CREATED or FAILED).
 * @param trigger      the execution trigger metadata containing plugin output variables and log file URI.
 * @param labels       the execution labels including system labels (FROM, CORRELATION_ID).
 * @param flowRevision the flow revision at evaluation time.
 */
public record TriggerEvaluationResult(
    @JsonProperty String executionId,
    @JsonProperty State.Type stateType,
    @JsonProperty ExecutionTrigger trigger,
    @JsonProperty @Nullable List<Label> labels,
    @JsonProperty @Nullable Integer flowRevision,
    @JsonProperty @Nullable Instant scheduleDate,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(implementation = Object.class)
    Map<String, Object> inputs
) {

    /**
     * Extracts a lightweight result from a full {@link Execution} (worker-side).
     *
     * @param execution the full execution created by the trigger evaluation.
     * @return a new {@link TriggerEvaluationResult}.
     */
    public static TriggerEvaluationResult from(Execution execution) {
        return new TriggerEvaluationResult(
            execution.getId(),
            execution.getState().getCurrent(),
            execution.getTrigger(),
            execution.getLabels(),
            execution.getFlowRevision(),
            execution.getScheduleDate(),
            execution.getInputs()
        );
    }

    /**
     * Returns a copy with a different state type.
     */
    public TriggerEvaluationResult withState(State.Type state) {
        return new TriggerEvaluationResult(executionId, state, trigger, labels, flowRevision, scheduleDate, inputs);
    }

    /**
     * Reconstructs a full {@link Execution} from this lightweight result.
     *
     * @param triggerId the trigger identifier providing namespace, flowId, and tenantId.
     * @return a reconstructed {@link Execution}.
     */
    public Execution toExecution(TriggerId triggerId) {
        State state = State.Type.CREATED.equals(stateType)
            ? new State()
            : new State().withState(stateType);

        return Execution.builder()
            .id(executionId)
            .tenantId(triggerId.getTenantId())
            .namespace(triggerId.getNamespace())
            .flowId(triggerId.getFlowId())
            .flowRevision(flowRevision)
            .state(state)
            .trigger(trigger)
            .labels(labels)
            .scheduleDate(scheduleDate)
            .inputs(inputs)
            .build();
    }
}
