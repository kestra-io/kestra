package io.kestra.core.models.tasks;

import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.utils.IdUtils;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ResolvedTask {
    @NotNull
    Task task;

    String value;

    Integer iteration;

    String parentId;

    @Builder.Default
    RetryBudgetOwner retryBudgetOwner = RetryBudgetOwner.TASK_RUN;

    public NextTaskRun toNextTaskRun(Execution execution) {
        return new NextTaskRun(
            TaskRun.of(execution, this),
            this.getTask()
        );
    }

    public NextTaskRun toNextTaskRunIncrementIteration(Execution execution, Integer iteration) {
        return new NextTaskRun(
            TaskRun.of(execution, this).withIteration(iteration != null ? iteration : 0),
            this.getTask()
        );
    }

    public static ResolvedTask of(Task task) {
        return ResolvedTask.builder()
            .task(task)
            .build();
    }

    public static List<ResolvedTask> of(List<Task> tasks) {
        if (tasks == null) {
            return null;
        }

        return tasks
            .stream()
            .map(ResolvedTask::of)
            .toList();
    }

    public String uid() {
        return IdUtils.fromParts(this.task.getId(), this.value);
    }

    /**
     * The task run whose attempts a task's retry policy is counted against. Resolution-time only, never persisted:
     * the flowable sets it each time it resolves its children.
     */
    public enum RetryBudgetOwner {
        TASK_RUN,
        /** The task only runs inside its parent, so retrying it reruns the parent under a fresh task run. */
        PARENT_TASK_RUN
    }
}
