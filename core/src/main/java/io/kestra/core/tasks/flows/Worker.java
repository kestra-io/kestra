package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.DynamicTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks sequentially sharing the same filesystem",
    description = "By default, Kestra will launch each task on a fresh filesystem and on a new worker instance.\n" +
        "This task will run sequentially keeping the same filesystem allowing reuse previous task file on next tasks and" +
        "keep tracking of execution time for each task. This task is mostly useful when working with large filesystem operation."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: worker",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: worker",
                "    type: io.kestra.core.tasks.flows.Worker",
                "    tasks:",
                "      - id: first",
                "        type: io.kestra.core.tasks.scripts.Bash",
                "        commands:",
                "        - 'echo \"{{ taskrun.id }}\" > {{ workingDir }}/stay.txt'",
                "      - id: second",
                "        type: io.kestra.core.tasks.scripts.Bash",
                "        commands:",
                "        - |",
                "          echo '::{\"outputs\": {\"stay\":\"'$(cat {{ workingDir }}/stay.txt)'\"}}::'"
            }
        )
    }
)
public class Worker extends Sequential implements DynamicTask {
    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        if (execution.hasFailed(childTasks, parentTaskRun)) {
            return super.resolveNexts(runContext, execution, parentTaskRun);
        }

        return Collections.emptyList();
    }

    public WorkerTask workerTask(TaskRun parent, Task task, RunContext runContext) {
        return WorkerTask.builder()
            .task(task)
            .taskRun(TaskRun.builder()
                .id(IdUtils.create())
                .executionId(parent.getExecutionId())
                .namespace(parent.getNamespace())
                .flowId(parent.getFlowId())
                .taskId(task.getId())
                .parentTaskRunId(parent.getId())
                .state(new State())
                .build()
            )
            .runContext(runContext)
            .build();
    }
}
