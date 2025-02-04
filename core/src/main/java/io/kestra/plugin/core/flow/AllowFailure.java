package io.kestra.plugin.core.flow;

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
    title = "Allow a list of tasks to fail without stopping the execution of downstream tasks in the flow.",
    description = "If any child task of the `AllowFailure` task fails, the flow will stop executing this block of tasks (i.e. the next tasks in the `AllowFailure` block will no longer be executed), but the flow execution of the tasks, following the `AllowFailure` task, will continue."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: allow_failure
                namespace: company.team

                tasks:
                  - id: sequential
                    type: io.kestra.plugin.core.flow.AllowFailure
                    tasks:
                     - id: ko
                       type: io.kestra.plugin.scripts.shell.Commands
                       commands:
                        - 'exit 1'
                  - id: last
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ task.id }} > {{ taskrun.startDate }}"
                """
        )
    },
    aliases = "io.kestra.core.tasks.flows.AllowFailure"
)
public class AllowFailure extends Sequential implements FlowableTask<VoidOutput> {
    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> resolvedTasks = this.childTasks(runContext, parentTaskRun);
        List<ResolvedTask> resolvedErrors = FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun);
        List<ResolvedTask> resolvedFinally = FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun);

        Optional<State.Type> type = FlowableUtils.resolveState(
            execution,
            resolvedTasks,
            resolvedErrors,
            resolvedFinally,
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );

        if (type.isEmpty()) {
            return type;
        } else {
            Optional<State.Type> normalState = FlowableUtils.resolveState(
                execution,
                resolvedTasks,
                null,
                resolvedFinally,
                parentTaskRun,
                runContext,
                this.isAllowFailure(),
                this.isAllowWarning()
            );

            if (normalState.isPresent() && normalState.get().isFailed()) {
                return Optional.of(State.Type.WARNING);
            } else {
                return type;
            }
        }
    }
}
