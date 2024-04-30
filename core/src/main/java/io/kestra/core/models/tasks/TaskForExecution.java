package io.kestra.core.models.tasks;

import io.kestra.core.models.flows.Input;
import io.kestra.core.tasks.flows.Pause;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public class TaskForExecution implements TaskInterface {
    protected String id;

    protected String type;

    protected List<Input<?>> inputs;

    public static TaskForExecution of(Task task) {
        List<Input<?>> inputs = null;

        if (task instanceof Pause pauseTask) {
            inputs = pauseTask.getOnResume();
        }

        return TaskForExecution.builder()
            .id(task.getId())
            .type(task.getType())
            .inputs(inputs)
            .build();
    }
}
