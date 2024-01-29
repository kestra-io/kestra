package io.kestra.core.models.tasks;

import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;

import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.constraints.NotNull;

@Builder
@Value
public class ResolvedTask {
    @NotNull
    Task task;

    String value;

    String parentId;

    public NextTaskRun toNextTaskRun(Execution execution) {
        return new NextTaskRun(
            TaskRun.of(execution, this),
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
            .collect(Collectors.toList());
    }
}
