package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pause current execution and wait for a manual approval or a delay"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: pause",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: pause",
                "    type: io.kestra.core.tasks.flows.Pause",
                "    tasks:",
                "     - id: ko",
                "       type: io.kestra.core.tasks.scripts.Bash",
                "       commands:",
                "        - 'echo \"trigger after manual restart\"'",
                "  - id: last",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class Pause extends Sequential implements FlowableTask<VoidOutput> {
    @Schema(
        title = "Duration of the pause.",
        description = "If null and no timeout, a manual approval is needed, if not, the delay before continuing the execution"
    )
    @PluginProperty
    private Duration delay;

    @Schema(
        title = "Timeout of the pause.",
        description = "If null and no delay, a manual approval is needed, else a manual approval is needed before the timeout or the task will fail"
    )
    @PluginProperty
    private Duration timeout;

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun) || parentTaskRun.getState().getCurrent() == State.Type.PAUSED) {
            return new ArrayList<>();
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }

    private boolean needPause(TaskRun parentTaskRun) {
        return parentTaskRun.getState().getCurrent() == State.Type.RUNNING &&
            parentTaskRun.getState().getHistories().get(parentTaskRun.getState().getHistories().size() - 2).getState() != State.Type.PAUSED;
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun)) {
            return Optional.of(State.Type.PAUSED);
        }

        return super.resolveState(runContext, execution, parentTaskRun);
    }
}
