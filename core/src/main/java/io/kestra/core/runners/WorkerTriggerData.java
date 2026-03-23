package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowInterface;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wire-format for the execution context sent with a {@link WorkerTrigger}.
 * <p>
 * Replaces the full {@link ConditionContext} on the wire. The {@link RunContext}
 * variables are stripped of worker-reconstructable keys, while the {@link FlowInterface}
 * is kept for plugin API compatibility (trigger {@code evaluate()} receives a
 * {@link ConditionContext} that exposes the flow).
 *
 * @param variables          RunContext variables minus worker-reconstructed keys.
 * @param secretInputs       List of input keys that are secrets (for log masking).
 * @param traceParent        OpenTelemetry trace parent for distributed tracing.
 * @param flow               The flow (kept for plugin API — triggers access it via ConditionContext).
 * @param conditionVariables Additional condition-specific variables.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkerTriggerData(
    Map<String, Object> variables,
    List<String> secretInputs,
    @Nullable String traceParent,
    FlowInterface flow,
    Map<String, Object> conditionVariables
) {

    /** Keys excluded from the wire format — the worker reconstructs them locally. */
    static final Set<String> WORKER_RECONSTRUCTED_KEYS = Set.of(
        "envs",
        "globals",
        "kestra",
        RunVariables.SECRET_CONSUMER_VARIABLE_NAME
    );

    /**
     * Creates a {@link WorkerTriggerData} from a {@link ConditionContext}, stripping
     * RunContext keys that the worker can reconstruct locally.
     *
     * @param conditionContext the ConditionContext to extract wire data from
     * @return a new WorkerTriggerData suitable for serialization
     */
    public static WorkerTriggerData from(ConditionContext conditionContext) {
        RunContext runContext = conditionContext.getRunContext();
        Map<String, Object> filtered = new HashMap<>(runContext.getVariables());
        WORKER_RECONSTRUCTED_KEYS.forEach(filtered::remove);
        return new WorkerTriggerData(
            filtered,
            runContext.getSecretInputs(),
            runContext.getTraceParent(),
            conditionContext.getFlow(),
            conditionContext.getVariables()
        );
    }
}
