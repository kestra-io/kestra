package org.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.avro.reflect.Nullable;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.tasks.retrys.AbstractRetry;
import org.kestra.core.runners.RunContext;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
abstract public class Task {
    @NotNull
    protected String id;

    @NotNull
    protected String type;

    @Nullable
    protected AbstractRetry retry;

    @Nullable
    protected Integer timeout;

    @Nullable
    @Valid
    protected List<Task> errors;

    public Optional<Task> findById(String id, RunContext runContext, TaskRun taskRun) {
        if (this.getId().equals(id)) {
            return Optional.of(this);
        }

        if (this instanceof FlowableTask) {
            Optional<Task> childs = ((FlowableTask) this).childTasks(runContext, taskRun)
                .stream()
                .map(resolvedTask -> resolvedTask.getTask().findById(id, runContext, taskRun))
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
                .flatMap(task -> task.findById(id, runContext, taskRun).stream())
                .findFirst();
        }

        if (this instanceof FlowableTask && ((FlowableTask) this).getErrors() != null) {
            Optional<Task> errorChilds = ((FlowableTask) this).getErrors()
                .stream()
                .map(task -> task.findById(id, runContext, taskRun))
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
