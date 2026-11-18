package io.kestra.webserver.models.api;

import io.kestra.core.models.executions.LoopRun;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Slim projection of {@link LoopRun}, without the parent {@code Execution} it carries for
 * internal use. Used wherever a loop iteration's identity needs to reach the API or the UI,
 * so a list of iterations doesn't each embed a copy of the execution that contains the loop.
 */
public record ApiLoopRun(@NotNull String taskId,
    @NotNull String taskRunId,
    int index,
    @Nullable String key,
    @NotNull String value,
    List<Parent> parents) {
    public record Parent(int index, @Nullable String key, @NotNull String value) {
        public static Parent of(LoopRun.Parent parent) {
            return new Parent(parent.index(), parent.key(), parent.value());
        }
    }

    public static ApiLoopRun of(LoopRun loopRun) {
        if (loopRun == null) {
            return null;
        }

        return new ApiLoopRun(
            loopRun.taskId(),
            loopRun.taskRunId(),
            loopRun.index(),
            loopRun.key(),
            loopRun.value(),
            loopRun.parents() == null ? null : loopRun.parents().stream().map(Parent::of).toList()
        );
    }
}
