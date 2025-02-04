package io.kestra.plugin.core.execution;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutionUpdatableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Exit the execution: terminate it in the state defined by the property `state`.",
    description = "Note that if this execution has running tasks, for example in a parallel branch, the tasks will not be terminated except if `state` is set to `KILLED`."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: exit
                namespace: company.team

                inputs:
                  - id: state
                    type: SELECT
                    values:
                      - CONTINUE
                      - END
                    defaults: CONTINUE

                tasks:
                  - id: if
                    type: io.kestra.plugin.core.flow.If
                    condition: "{{inputs.state == 'CONTINUE'}}"
                    then:
                      - id: hello
                        type: io.kestra.plugin.core.log.Log
                        message: I'm continuing
                    else:
                      - id: exit
                        type: io.kestra.plugin.core.execution.Exit
                        state: KILLED
                  - id: end
                    type: io.kestra.plugin.core.log.Log
                    message: I'm ending
                """
        )
    }
)
public class Exit extends Task implements ExecutionUpdatableTask {
    @NotNull
    @Schema(
        title = "The execution exit state",
        description = "Using `KILLED` will end existing running tasks, and any other execution with a different state will continue to run."
    )
    @Builder.Default
    private Property<ExitState> state = Property.of(ExitState.SUCCESS);

    @Override
    public Execution update(Execution execution, RunContext runContext) throws Exception {
        State.Type exitState = executionState(runContext);

        // if the state is killed, we send a kill event and end here
        if (exitState == State.Type.KILLED) {
            QueueInterface<ExecutionKilled> killQueue = ((DefaultRunContext) runContext).getApplicationContext().getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.KILL_NAMED));
            killQueue.emit(ExecutionKilledExecution
                .builder()
                .state(ExecutionKilled.State.REQUESTED)
                .executionId(execution.getId())
                .isOnKillCascade(false)
                .tenantId(execution.getTenantId())
                .build()
            );
            return execution.withState(exitState);
        }

        return execution.findLastNotTerminated()
            .map(taskRun -> {
                try {
                    TaskRun newTaskRun = taskRun.withState(exitState);
                    Execution newExecution = execution.withTaskRun(newTaskRun);
                    // ends all parents
                    while (newTaskRun.getParentTaskRunId() != null) {
                        newTaskRun = newExecution.findTaskRunByTaskRunId(newTaskRun.getParentTaskRunId()).withState(exitState);
                        newExecution = execution.withTaskRun(newTaskRun);
                    }
                    return newExecution;
                } catch (InternalException e) {
                    // in case we cannot update the last not terminated task run, we ignore it
                    return execution;
                }
            })
            .orElse(execution)
            .withState(exitState);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution) throws IllegalVariableEvaluationException {
        return Optional.of(executionState(runContext));
    }

    private State.Type executionState(RunContext runContext) throws IllegalVariableEvaluationException {
        return switch (runContext.render(this.state).as(ExitState.class).orElseThrow()) {
            case ExitState.SUCCESS -> State.Type.SUCCESS;
            case WARNING -> State.Type.WARNING;
            case KILLED -> State.Type.KILLED;
            case FAILED -> State.Type.FAILED;
            case CANCELED -> State.Type.CANCELLED;
        };
    }

    public enum ExitState {
        SUCCESS, WARNING, KILLED, FAILED, CANCELED
    }
}
