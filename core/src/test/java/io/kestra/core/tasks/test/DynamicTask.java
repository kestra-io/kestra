package io.kestra.core.tasks.test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class DynamicTask extends Task implements RunnableTask<VoidOutput> {

    @Builder.Default
    private Boolean fail = false;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        State state = fail ? new State(State.Type.FAILED) : new State(State.Type.SUCCESS);

        WorkerTaskResult workerTaskResult = WorkerTaskResult.builder()
            .taskRun(TaskRun.builder()
                .id(IdUtils.create())
                .namespace(runContext.render("{{ flow.namespace }}"))
                .flowId(runContext.render("{{ flow.id }}"))
                .taskId(IdUtils.create())
                .value(runContext.render("{{ taskrun.id }}"))
                .executionId(runContext.render("{{ execution.id }}"))
                .parentTaskRunId(runContext.render("{{ taskrun.id }}"))
                .state(state)
                .attempts(List.of(TaskRunAttempt.builder()
                    .state(state)
                    .build()
                ))
                .build()
            )
            .build();

        runContext.dynamicWorkerResult(List.of(workerTaskResult));

        if (workerTaskResult.getTaskRun().getState().isFailed()) {
            throw new Exception("Task failed");
        }

        return null;
    }
}
