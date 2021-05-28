package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.tasks.flows.Flow;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTaskExecution {
    @NotNull
    private TaskRun taskRun;

    @NotNull
    private Flow task;

    @NotNull
    private Execution execution;

    @NotNull
    private RunContext runContext;
}
