package io.kestra.core.models.hierarchies;

import lombok.Getter;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import java.util.List;

@Getter
public class GraphTask extends AbstractGraphTask {
    public GraphTask() {
        super();
    }

    public GraphTask(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(task, taskRun, values, relationType);
    }
}
