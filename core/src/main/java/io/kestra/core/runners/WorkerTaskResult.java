package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.event.DispatchEvent;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
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

    /** Results already emitted for the same WorkingDirectory that must be joined before this result. */
    @With
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<WorkerTaskResultPayload> precedingResults;

    public WorkerTaskResult(TaskRun taskRun) {
        this(taskRun, new ArrayList<>(1), null, List.of()); // there are usually very few dynamic task runs, so we init the list with a capacity of 1
    }

    public WorkerTaskResult(TaskRun taskRun, Map<String, Object> outputs) {
        this(taskRun, new ArrayList<>(1), outputs, List.of()); // there are usually very few dynamic task runs, so we init the list with a capacity of 1
    }

    public WorkerTaskResult(TaskRun taskRun, List<TaskRun> dynamicTaskRuns, Map<String, Object> outputs) {
        this(taskRun, dynamicTaskRuns, outputs, List.of());
    }

    @JsonCreator
    public WorkerTaskResult(
        @JsonProperty("taskRun") TaskRun taskRun,
        @JsonProperty("dynamicTaskRuns") List<TaskRun> dynamicTaskRuns,
        @JsonProperty("outputs") Map<String, Object> outputs,
        @JsonProperty("precedingResults") List<WorkerTaskResultPayload> precedingResults
    ) {
        this.taskRun = taskRun;
        this.dynamicTaskRuns = dynamicTaskRuns;
        this.outputs = outputs;
        this.precedingResults = precedingResults == null ? List.of() : precedingResults;
    }

    public WorkerTaskResult withoutOutputs() {
        return withOutputs(null).withPrecedingResults(precedingResults.stream().map(WorkerTaskResultPayload::withoutOutputs).toList());
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

    public record WorkerTaskResultPayload(
        @NotNull TaskRun taskRun,
        List<TaskRun> dynamicTaskRuns,
        @Nullable @JsonInclude(JsonInclude.Include.ALWAYS) Map<String, Object> outputs
    ) {
        public static WorkerTaskResultPayload from(WorkerTaskResult result) {
            return new WorkerTaskResultPayload(result.getTaskRun(), result.getDynamicTaskRuns(), result.getOutputs());
        }

        public WorkerTaskResult toWorkerTaskResult() {
            return new WorkerTaskResult(taskRun, dynamicTaskRuns, outputs);
        }

        public WorkerTaskResultPayload withoutOutputs() {
            return new WorkerTaskResultPayload(taskRun, dynamicTaskRuns, null);
        }
    }
}
