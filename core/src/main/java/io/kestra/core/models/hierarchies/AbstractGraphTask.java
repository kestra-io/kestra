package io.kestra.core.models.hierarchies;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ToString
@Getter
@Introspected
public abstract class AbstractGraphTask extends AbstractGraph {
    private final Task task;
    private final TaskRun taskRun;
    private final List<String> values;
    private final RelationType relationType;

    public AbstractGraphTask(String uid, Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(uid);

        this.task = task;
        this.taskRun = taskRun;
        this.values = values;
        this.relationType = relationType;
    }

    public AbstractGraphTask(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
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
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        AbstractGraphTask that = (AbstractGraphTask) object;

        return Objects.equals(this.getUid(), that.getUid());
    }
}
