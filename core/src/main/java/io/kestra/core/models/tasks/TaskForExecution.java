package io.kestra.core.models.tasks;

import java.lang.reflect.Method;
import java.util.List;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.core.flow.Pause;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public class TaskForExecution implements TaskInterface {
    protected String id;

    protected String type;

    protected String version;

    protected List<TaskForExecution> tasks;

    protected List<Input<?>> inputs;

    protected ExecutableTask.SubflowId subflowId;

    protected TaskRunnerRef taskRunner;

    /** Minimal runner reference — only the type is needed by the UI. */
    @Builder
    public record TaskRunnerRef(String type) {}

    public static TaskForExecution of(TaskInterface task) {
        List<Input<?>> inputs = null;

        if (task instanceof Pause pauseTask) {
            inputs = pauseTask.getOnResume();
        }

        TaskForExecutionBuilder<?, ?> taskForExecutionBuilder = TaskForExecution.builder()
            .id(task.getId())
            .type(task.getType())
            .inputs(inputs);

        if (task instanceof ExecutableTask<?> executableTask) {
            taskForExecutionBuilder.subflowId(executableTask.subflowId());
        }

        if (task instanceof FlowableTask<?> flowable) {
            taskForExecutionBuilder.tasks(flowable.allChildTasks().stream().map(TaskForExecution::of).toList());
        }

        try {
            Method m = task.getClass().getMethod("getTaskRunner");
            TaskRunner<?> runner = (TaskRunner<?>) m.invoke(task);
            if (runner != null) {
                taskForExecutionBuilder.taskRunner(TaskRunnerRef.builder().type(runner.getType()).build());
            }
        } catch (ReflectiveOperationException ignored) {
            // task has no taskRunner property
        }

        return taskForExecutionBuilder.build();
    }
}
