package org.kestra.core.models.hierarchies;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.tasks.Task;

import java.util.List;

@Value
@Builder
public class TaskTree {
    private boolean folded;

    private Task task;

    private RelationType relation;

    private List<String> groups;

    private List<ParentTaskTree> parent;

    @With
    private TaskRun taskRun;
}
