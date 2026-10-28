package io.kestra.webserver.models.api;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LoopRun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Slim projection of {@link LoopRun}, without the parent {@code Execution} it carries for
 * internal use. Used wherever a loop iteration's identity needs to reach the API or the UI,
 * so a list of iterations doesn't each embed a copy of the execution that contains the loop.
 */
public record ApiLoopRun(
    @NotNull String taskId,
    @NotNull String taskRunId,
    int index,
    @Nullable String key,
    @NotNull String value,
    List<Parent> parents,
    @Nullable String rootExecutionId
) {

    public ApiLoopRun(
        @NotNull String taskId,
        @NotNull String taskRunId,
        int index,
        @Nullable String key,
        @NotNull String value,
        List<Parent> parents
    ) {
        this(taskId, taskRunId, index, key, value, parents, null);
    }

    public record Parent(
        int index,
        @Nullable String key,
        @NotNull String value,
        @Nullable String executionId,
        @Nullable String taskId
    ) {

        public Parent(int index, @Nullable String key, @NotNull String value) {
            this(index, key, value, null, null);
        }

        public static Parent of(LoopRun.Parent parent) {
            return new Parent(parent.index(), parent.key(), parent.value(), parent.executionId(), parent.taskId());
        }
    }

    public static ApiLoopRun of(LoopRun loopRun) {
        if (loopRun == null) {
            return null;
        }

        String rootExecutionId = loopRun.rootExecutionId();
        List<Parent> parents = null;

        if (loopRun.parents() != null) {
            boolean hasMissingIds = loopRun.parents().stream().anyMatch(p -> p.executionId() == null);
            if (hasMissingIds && loopRun.parent() != null) {
                List<Execution> loopAncestors = new ArrayList<>();
                Execution ancestor = loopRun.parent();
                while (ancestor != null && ancestor.getLoopRun() != null) {
                    loopAncestors.add(ancestor);
                    ancestor = ancestor.getLoopRun().parent();
                }
                if (rootExecutionId == null && ancestor != null) {
                    rootExecutionId = ancestor.getId();
                }
                Collections.reverse(loopAncestors);

                List<Parent> enriched = new ArrayList<>();
                for (int i = 0; i < loopRun.parents().size(); i++) {
                    LoopRun.Parent p = loopRun.parents().get(i);
                    String execId = p.executionId();
                    String taskId = p.taskId();
                    if (execId == null && i < loopAncestors.size()) {
                        Execution match = loopAncestors.get(i);
                        execId = match.getId();
                        if (taskId == null && match.getLoopRun() != null) {
                            taskId = match.getLoopRun().taskId();
                        }
                    }
                    enriched.add(new Parent(p.index(), p.key(), p.value(), execId, taskId));
                }
                parents = enriched;
            } else {
                parents = loopRun.parents().stream().map(Parent::of).toList();
            }
        }

        if (rootExecutionId == null && loopRun.parent() != null) {
            Execution ancestor = loopRun.parent();
            while (ancestor != null && ancestor.getLoopRun() != null) {
                ancestor = ancestor.getLoopRun().parent();
            }
            if (ancestor != null) {
                rootExecutionId = ancestor.getId();
            }
        }

        return new ApiLoopRun(
            loopRun.taskId(),
            loopRun.taskRunId(),
            loopRun.index(),
            loopRun.key(),
            loopRun.value(),
            parents,
            rootExecutionId
        );
    }
}
