package io.kestra.core.models.tasks;

import java.util.List;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.flow.Approval;
import io.kestra.plugin.core.flow.PausableTask;

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

    protected Approval.Decisions decisions;

    protected Property<Approval.CommentRequired> commentRequired;

    public static TaskForExecution of(TaskInterface task) {
        List<Input<?>> inputs = null;

        if (task instanceof PausableTask pausableTask) {
            inputs = pausableTask.resumeInputs();
        }

        TaskForExecutionBuilder<?, ?> taskForExecutionBuilder = TaskForExecution.builder()
            .id(task.getId())
            .type(task.getType())
            .inputs(inputs);

        if (task instanceof Approval approval) {
            taskForExecutionBuilder.decisions(approval.getDecisions()).commentRequired(approval.getCommentRequired());
        }

        if (task instanceof ExecutableTask<?> executableTask) {
            taskForExecutionBuilder.subflowId(executableTask.subflowId());
        }

        if (task instanceof FlowableTask<?> flowable) {
            taskForExecutionBuilder.tasks(flowable.allChildTasks().stream().map(TaskForExecution::of).toList());
        }

        return taskForExecutionBuilder.build();
    }
}
