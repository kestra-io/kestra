package io.kestra.core.models.hierarchies;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;

import java.util.List;

@Getter
public class GraphTask extends AbstractGraphTask {
    public GraphTask(String uid, Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(uid, task, taskRun, values, relationType);
    }

    public GraphTask(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(task, taskRun, values, relationType);
    }
}
