package io.kestra.webserver.models.api;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.scheduler.model.TriggerState;

/**
 * API DTO for exposing trigger state to the UI.
 * <p>
 * Excludes internal scheduler fields ({@code tenantId}, {@code vnode}, {@code lastEventId}, {@code type}).
 */
public record ApiTriggerState(
    String namespace,
    String flowId,
    String triggerId,
    Instant updatedAt,
    Instant evaluatedAt,
    Instant nextEvaluationDate,
    Backfill backfill,
    List<State.Type> stopAfter,
    boolean disabled,
    boolean locked,
    String workerId
) {
    public static ApiTriggerState from(TriggerState state) {
        return new ApiTriggerState(
            state.getNamespace(),
            state.getFlowId(),
            state.getTriggerId(),
            state.getUpdatedAt(),
            state.getEvaluatedAt(),
            state.getNextEvaluationDate(),
            state.getBackfill(),
            state.getStopAfter(),
            state.isDisabled(),
            state.isLocked(),
            state.getWorkerId()
        );
    }
}
