package io.kestra.webserver.services.playground;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * A replay of {@code buildOnExecutionId} from {@code replayFromTaskRunId}, or a fresh run when {@code freshReason} is not NONE.
 */
public record PlaygroundPlan(
    @Nullable String buildOnExecutionId,
    @Nullable String replayFromTaskRunId,
    @Nullable String replayFromTaskId,
    @NotNull List<String> reusedTaskIds,
    @NotNull List<String> breakpoints,
    @NotNull PlaygroundFreshReason freshReason) {
    static PlaygroundPlan fresh(PlaygroundFreshReason reason, List<String> breakpoints) {
        return new PlaygroundPlan(null, null, null, List.of(), breakpoints, reason);
    }
}
