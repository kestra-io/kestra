package io.kestra.core.models.hierarchies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.tasks.TaskInterface;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Introspected // without it, there is a deserialization issue with GraphTask
public abstract class AbstractGraphTask extends AbstractGraph {
    @Setter
    private TaskInterface task;
    private final TaskRun taskRun;
    private final List<String> values;
    private final RelationType relationType;
    /** Task properties with Pebble expressions resolved for display. Null when resolution was not attempted. */
    private Map<String, Object> renderedProperties;

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

    /** Sets display-resolved properties on this node and returns {@code this} for chaining. */
    public AbstractGraphTask withRenderedProperties(Map<String, Object> resolved) {
        this.renderedProperties = resolved;
        return this;
    }

    @Override
    public AbstractGraph forExecution() {
        this.setTask(TaskForExecution.of(this.getTask()));
        // forExecution() reduces the visible task to a safe subset for principals that only have
        // EXECUTION-READ. The resolved properties hold the full task configuration, so they must be
        // stripped here too, otherwise they would bypass that reduction.
        this.renderedProperties = null;

        return this;
    }
}
