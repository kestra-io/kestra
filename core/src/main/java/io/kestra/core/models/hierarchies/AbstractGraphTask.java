package io.kestra.core.models.hierarchies;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.TaskInterface;
import io.kestra.core.models.tasks.TaskForExecution;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ToString
@Getter
@Introspected
public abstract class AbstractGraphTask extends AbstractGraph {
    @Setter
    private TaskInterface task;
    private final TaskRun taskRun;
    private final List<String> values;
    private final RelationType relationType;

    public AbstractGraphTask(String uid, TaskInterface task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(uid);

        this.task = task;
        this.taskRun = taskRun;
        this.values = values;
        this.relationType = relationType;
    }

    public AbstractGraphTask(TaskInterface task, TaskRun taskRun, List<String> values, RelationType relationType) {
        this(task.getId(), task, taskRun, values, relationType);
    }

    @Override
    public String getLabel() {
        String[] splitUid = this.getUid().split("\\.");
        return splitUid[splitUid.length - 1] + (this.getTaskRun() != null ? " > " + this.getTaskRun().getValue() + " (" + this.getTaskRun().getId() + ")" : "");
    }

    @Override
    public String getUid() {
        List<String> list = new ArrayList<>();

        list.add(this.uid);

        if (values != null) {
            list.addAll(values);
        }

        return String.join("_", list);
    }

    @Override
    public AbstractGraph forExecution() {
        this.setTask(TaskForExecution.of(this.getTask()));

        return this;
    }
}
