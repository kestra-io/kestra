package io.kestra.core.models.hierarchies;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import java.util.ArrayList;
import java.util.List;

@ToString
@Getter
@Introspected
public abstract class AbstractGraphTask extends AbstractGraph {
    private final Task task;
    private final TaskRun taskRun;
    private final List<String> values;
    private final RelationType relationType;

    public AbstractGraphTask() {
        super();

        this.task = null;
        this.taskRun = null;
        this.values = null;
        this.relationType = null;
    }

    public AbstractGraphTask(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super();

        this.task = task;
        this.taskRun = taskRun;
        this.values = values;
        this.relationType = relationType;
    }

    @Override
    public String getLabel() {
        return this.getUid() + (this.getTaskRun() != null ? " > " + this.getTaskRun().getValue() + " (" + this.getTaskRun().getId() + ")" : "");
    }

    @Override
    public String getUid() {
        List<String> list = new ArrayList<>();

        if (this.task != null) {
            list.add(this.task.getId());
        }

        if (values != null) {
            list.addAll(values);
        }

        return String.join("_", list);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;

        if (!(o instanceof AbstractGraphTask)) {
            return false;
        }

        return o.hashCode() == this.hashCode();
    }

    @Override
    public int hashCode() {
        return (this.uid + this.getClass().getName()).hashCode();
    }
}
