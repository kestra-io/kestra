package io.kestra.webserver.models.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;

import jakarta.validation.constraints.NotNull;

/**
 * API DTO for exposing trigger state to the UI.
 * <p>
 * Excludes internal scheduler fields ({@code tenantId}, {@code vnode}, {@code lastEventId}). The
 * scheduler's {@code type} is exposed as {@code kind} to not clash with the trigger definition's
 * {@code type} (the plugin class) when both are merged by API consumers.
 */
public record ApiTriggerState(
    @NotNull String namespace,
    @NotNull String flowId,
    @NotNull String triggerId,
    @NotNull Instant updatedAt,
    Instant evaluatedAt,
    Instant nextEvaluationDate,
    Backfill backfill,
    List<State.Type> stopAfter,
    boolean disabled,
    boolean locked,
    String workerId,
    Instant lastTriggeredDate,
    String executionId,
    TriggerType kind
) {
    public static ApiTriggerState from(TriggerState state) {
        return new ApiTriggerState(
            state.getNamespace(),
            state.getFlowId(),
            state.getTriggerId(),
            truncate(state.getUpdatedAt()),
            truncate(state.getEvaluatedAt()),
            truncate(state.getNextEvaluationDate()),
            state.getBackfill(),
            state.getStopAfter(),
            state.isDisabled(),
            state.isLocked(),
            state.getWorkerId(),
            truncate(state.getLastTriggeredDate()),
            state.getExecutionId(),
            state.getType()
        );
    }

    private static Instant truncate(Instant instant) {
        return instant != null ? instant.truncatedTo(ChronoUnit.SECONDS) : null;
    }
}
