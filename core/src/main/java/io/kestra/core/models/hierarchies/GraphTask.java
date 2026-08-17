package io.kestra.core.models.hierarchies;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import lombok.Getter;

@Getter
public class GraphTask extends AbstractGraphTask {
    // Standard Jackson creator: AbstractGraphTask's @Introspected no longer serves as a fallback
    // creator source for Micronaut's Jackson 3 integration (see AbstractGraphTask's own comment),
    // and this hierarchy has no default constructor, so a creator must be explicit.
    @JsonCreator
    public GraphTask(
        String uid,
        Task task,
        TaskRun taskRun,
        List<String> values,
        RelationType relationType) {
        super(uid, task, taskRun, values, relationType);
    }

    public GraphTask(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(task, taskRun, values, relationType);
    }
}
