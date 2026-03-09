package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.event.DispatchEvent;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.With;

@Value
@AllArgsConstructor
@Builder
public class WorkerTaskResult implements DispatchEvent, HasUID {
    @NotNull
    @With
    TaskRun taskRun;

    List<TaskRun> dynamicTaskRuns;

    @Nullable
    @With
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Map<String, Object> outputs;

    public WorkerTaskResult(TaskRun taskRun) {
        this(taskRun, new ArrayList<>(1), null); // there are usually very few dynamic task runs, so we init the list with a capacity of 1
    }

    public WorkerTaskResult(TaskRun taskRun, Map<String, Object> outputs) {
        this(taskRun, new ArrayList<>(1), outputs); // there are usually very few dynamic task runs, so we init the list with a capacity of 1
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return taskRun.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String key() {
        return uid();
    }
}
