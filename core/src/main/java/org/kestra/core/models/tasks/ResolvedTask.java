package org.kestra.core.models.tasks;

import lombok.Builder;
import lombok.Value;
import org.apache.avro.reflect.Nullable;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Value
public class ResolvedTask {
    @NotNull
    protected Task task;

    @NotNull
    protected String value;

    @Nullable
    protected String parentId;

    public TaskRun toTaskRun(Execution execution) {
        return TaskRun.of(execution, this);
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
