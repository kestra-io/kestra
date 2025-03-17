package io.kestra.core.models.tasks;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ResolvedTask {
    @NotNull
    Task task;

    String value;

    Integer iteration;

    String parentId;

    public NextTaskRun toNextTaskRun(Execution execution) {
        return new NextTaskRun(
            TaskRun.of(execution, this),
            this.getTask()
        );
    }

    public NextTaskRun toNextTaskRunIncrementIteration(Execution execution, Integer iteration) {
        return new NextTaskRun(
            TaskRun.of(execution, this).withIteration(iteration != null ? iteration : 1),
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
}
