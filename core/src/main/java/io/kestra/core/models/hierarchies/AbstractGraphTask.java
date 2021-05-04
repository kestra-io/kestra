package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.utils.IdUtils;

import java.util.ArrayList;
import java.util.List;

@ToString
@Getter
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
public abstract class AbstractGraphTask {
    protected final String uid;
    @JsonInclude
    protected final String type;
    private final Task task;
    private final TaskRun taskRun;
    private final List<String> values;
    private final RelationType relationType;

    public AbstractGraphTask() {
        this.uid = IdUtils.create();
        this.type = this.getClass().getName();

        this.task = null;
        this.taskRun = null;
        this.values = null;
        this.relationType = null;
    }

    public AbstractGraphTask(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        this.uid = IdUtils.create();
        this.type = this.getClass().getName();

        this.task = task;
        this.taskRun = taskRun;
        this.values = values;
        this.relationType = relationType;
    }

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
