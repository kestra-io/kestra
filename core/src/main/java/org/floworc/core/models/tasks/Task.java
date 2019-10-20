package org.floworc.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.apache.avro.reflect.Nullable;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true)
@SuperBuilder
@Getter
@NoArgsConstructor
abstract public class Task {
    @NotNull
    protected String id;

    protected String type;

    @Nullable
    protected Retry retry;

    protected int timeout;

    protected List<Task> errors;

    public List<TaskRun> toTaskRun(Execution execution) {
        return Collections.singletonList(TaskRun.of(execution, this));
    }

    public Optional<Task> findById(String id) {
        if (this.getId().equals(id)) {
            return Optional.of(this);
        }

        if (this.errors != null) {
            return this.errors
                .stream()
                .flatMap(task -> task.findById(id).stream())
                .findFirst();
        }

        return Optional.empty();
    }
}
