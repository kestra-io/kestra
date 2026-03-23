package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.micronaut.core.annotation.Nullable;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wire-format carrying all data the worker needs to process a trigger.
 * <p>
 * This is a single flat data carrier — the worker reconstructs {@link TriggerContext},
 * {@link io.kestra.core.models.flows.GenericFlow}, {@link RunContext}, and
 * {@link ConditionContext} from these fields plus locally available beans.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkerTriggerData(
    // --- Trigger + flow identity ---
    String tenantId,
    String namespace,
    String flowId,
    ZonedDateTime date,
    @Nullable Integer flowRevision,
    @Nullable Map<String, Object> flowVariables,
    @Nullable List<Label> flowLabels,

    // --- RunContext wire data ---
    Map<String, Object> variables,
    List<String> secretInputs,
    @Nullable String traceParent,

    // --- Condition data ---
    Map<String, Object> conditionVariables
) {

    /**
     * Keys excluded from the variables map — rebuilt on the worker from
     * explicit fields (identity, flow metadata) and local beans (envs, globals, kestra).
     */
    static final Set<String> WORKER_RECONSTRUCTED_KEYS = Set.of(
        "flow",
        "labels",
        "envs",
        "globals",
        "kestra",
        RunVariables.SECRET_CONSUMER_VARIABLE_NAME
    );

    /**
     * Creates a {@link WorkerTriggerData} from a {@link ConditionContext} and
     * {@link TriggerContext}, stripping keys the worker can reconstruct locally.
     *
     * @param conditionContext the ConditionContext with RunContext and flow
     * @param triggerContext   the TriggerContext with identity and date
     * @return a new WorkerTriggerData suitable for wire transport
     */
    public static WorkerTriggerData from(ConditionContext conditionContext, TriggerContext triggerContext) {
        RunContext runContext = conditionContext.getRunContext();
        FlowInterface flow = conditionContext.getFlow();

        Map<String, Object> filtered = new HashMap<>(runContext.getVariables());
        WORKER_RECONSTRUCTED_KEYS.forEach(filtered::remove);

        return new WorkerTriggerData(
            triggerContext.getTenantId(),
            triggerContext.getNamespace(),
            triggerContext.getFlowId(),
            triggerContext.getDate(),
            flow.getRevision(),
            flow.getVariables(),
            flow.getLabels(),
            filtered,
            runContext.getSecretInputs(),
            runContext.getTraceParent(),
            conditionContext.getVariables()
        );
    }
}
