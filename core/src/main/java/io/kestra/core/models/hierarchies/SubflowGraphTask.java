package io.kestra.core.models.hierarchies;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.tasks.flows.Flow;
import lombok.Getter;

import java.util.List;

@Getter
public class SubflowGraphTask extends AbstractGraphTask {
    public SubflowGraphTask(Flow task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(task, taskRun, values, relationType);
    }

    @Override
    public Flow getTask() {
        return (Flow) super.getTask();
    }
}
