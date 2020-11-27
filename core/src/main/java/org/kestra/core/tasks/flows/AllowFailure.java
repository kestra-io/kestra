package org.kestra.core.tasks.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

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
                "namespace: org.kestra.tests",
                "",
                "tasks:",
                "  - id: sequential",
                "    type: org.kestra.core.tasks.flows.AllowFailure",
                "    tasks:",
                "     - id: ko",
                "       type: org.kestra.core.tasks.scripts.Bash",
                "       commands:",
                "        - 'exit 1'",
                "  - id: last",
                "    type: org.kestra.core.tasks.debugs.Return",
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
            parentTaskRun
        );

        if (type.isEmpty()) {
            return type;
        } else {
            Optional<State.Type> normalState = FlowableUtils.resolveState(
                execution,
                resolvedTasks,
                null,
                parentTaskRun
            );

            if (normalState.isPresent() && normalState.get().isFailed()) {
                return Optional.of(State.Type.WARNING);
            } else {
                return type;
            }
        }
    }
}
