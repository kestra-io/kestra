package io.kestra.core.models.hierarchies;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.tasks.TaskInterface;
import lombok.Getter;

import java.util.List;

@Getter
public class SubflowGraphTask extends AbstractGraphTask {
    public SubflowGraphTask(ExecutableTask<?> task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super((Task) task, taskRun, values, relationType);
    }

    public ExecutableTask<?> getExecutableTask() {
        TaskInterface task = super.getTask();
        if (task instanceof ExecutableTask) {
            return (ExecutableTask<?>) task;
        } else {
            return null;
        }
    }
}
