package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import lombok.With;

import java.util.Map;

@Data
@Builder
public class SubflowExecution<T extends Task & ExecutableTask<?>> implements HasUID {
    @NotNull
    private TaskRun parentTaskRun;

    @NotNull
    private T parentTask;

    @NotNull
    private Execution execution;

    @Nullable
    @With
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Map<String, Object> outputs;

    @Override
    public String uid() {
        return execution.getId();
    }
}
