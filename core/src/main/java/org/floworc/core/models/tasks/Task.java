package org.floworc.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.avro.reflect.Nullable;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder
@Getter
@NoArgsConstructor
abstract public class Task {
    @NotNull
    protected String id;

    @NotNull
    protected String type;

    @Nullable
    protected Retry retry;

    @Nullable
    protected Integer timeout;

    @Nullable
    protected List<Task> errors;

    public TaskRun toTaskRun(Execution execution) {
        return TaskRun.of(execution, this);
    }

    public Optional<Task> findById(String id) {
        if (this.getId().equals(id)) {
            return Optional.of(this);
        }

        if (this instanceof FlowableTask) {
            Optional<Task> childs = ((FlowableTask) this).childTasks()
                .stream()
                .map(task -> task.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            if (childs.isPresent()) {
                return childs;
            }
        }

        if (this.errors != null) {
            return this.errors
                .stream()
                .flatMap(task -> task.findById(id).stream())
                .findFirst();
        }

        if (this instanceof FlowableTask && ((FlowableTask) this).getErrors() != null) {
            Optional<Task> errorChilds = ((FlowableTask) this).getErrors()
                .stream()
                .map(task -> task.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            if (errorChilds.isPresent()) {
                return errorChilds;
            }
        }

        return Optional.empty();
    }
}
