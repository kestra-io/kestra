package io.kestra.core.tasks.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;

import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Allow a task to failed",
    description = "If any child tasks failed, the flow will stop child tasks, but will continue the main flow."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: allow-failure",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: sequential",
                "    type: io.kestra.core.tasks.flows.AllowFailure",
                "    tasks:",
                "     - id: ko",
                "       type: io.kestra.core.tasks.scripts.Bash",
                "       commands:",
                "        - 'exit 1'",
                "  - id: last",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class AllowFailure extends Sequential implements FlowableTask<VoidOutput> {
    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> resolvedTasks = this.childTasks(runContext, parentTaskRun);
        List<ResolvedTask> resolvedErrors = FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun);

        Optional<State.Type> type = FlowableUtils.resolveState(
            execution,
            resolvedTasks,
            resolvedErrors,
            parentTaskRun,
            runContext
        );

        if (type.isEmpty()) {
            return type;
        } else {
            Optional<State.Type> normalState = FlowableUtils.resolveState(
                execution,
                resolvedTasks,
                null,
                parentTaskRun,
                runContext
            );

            if (normalState.isPresent() && normalState.get().isFailed()) {
                return Optional.of(State.Type.WARNING);
            } else {
                return type;
            }
        }
    }
}
