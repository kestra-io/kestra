package io.kestra.core.worker.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * Lightweight representation of a trigger evaluation result.
 * <p>
 * This record carries only the data needed to reconstruct a full {@link Execution}
 * on the controller/scheduler side, avoiding the overhead of transporting
 * the full Execution object (23 fields) over gRPC when only a subset is populated.
 *
 * @param executionId   the generated execution ID.
 * @param stateType     the execution state type (CREATED or FAILED).
 * @param trigger       the execution trigger metadata containing plugin output variables and log file URI.
 * @param labels        the execution labels including system labels (FROM, CORRELATION_ID).
 * @param flowRevision  the flow revision at evaluation time.
 * @param flowVariables the flow-level variables (typically small or null).
 */
public record TriggerEvaluationResult(
    @JsonProperty String executionId,
    @JsonProperty State.Type stateType,
    @JsonProperty ExecutionTrigger trigger,
    @JsonProperty @Nullable List<Label> labels,
    @JsonProperty @Nullable Integer flowRevision,
    @JsonProperty @Nullable Map<String, Object> flowVariables
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
            execution.getVariables()
        );
    }

    /**
     * Reconstructs a full {@link Execution} from this lightweight result.
     * <p>
     * This method is self-contained — it only needs the {@link TriggerId}
     * (for namespace, flowId, tenantId) which is already available in
     * {@link WorkerTriggerResult}.
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
            .variables(flowVariables)
            .state(state)
            .trigger(trigger)
            .labels(labels)
            .build();
    }
}
