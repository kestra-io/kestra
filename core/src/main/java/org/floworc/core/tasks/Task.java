package org.floworc.core.tasks;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import org.apache.avro.reflect.Nullable;
import org.floworc.core.executions.Execution;
import org.floworc.core.executions.TaskRun;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true)
@Data
abstract public class Task {
    @NotNull
    private String id;

    private String type;

    @Nullable
    private Retry retry;

    private int timeout;

    private List<Task> errors;

    abstract public Void run() throws Exception;

    public List<TaskRun> toTaskRun(Execution execution) {
        return Collections.singletonList(TaskRun.of(execution, this));
    }

    public Optional<List<TaskRun>> getChildTaskRun(Execution execution) {
        return Optional.of(new ArrayList<>());
    }

    public Optional<Task> findById(String id) {
        if (this.getId().equals(id)) {
            return Optional.of(this);
        }

        return Optional.empty();
    }
}
