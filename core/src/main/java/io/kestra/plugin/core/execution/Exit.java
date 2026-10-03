package io.kestra.plugin.core.execution;

import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutionUpdatableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionTerminator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Terminate the current execution with a chosen state.",
    description = """
        Updates the execution to `SUCCESS`, `WARNING`, `FAILED`, `CANCELED`, or `KILLED`. When set to `KILLED`, an out-of-band kill event is sent so running task runs are stopped; other states simply mark the execution and parent task runs as finished.

        Use with care inside parallel branches: only `KILLED` stops sibling tasks."""
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
    private Property<ExitState> state = Property.ofValue(ExitState.SUCCESS);

    @Override
    public Execution update(Execution execution, RunContext runContext) throws Exception {
        // KILLED bypasses findLastNotTerminated; the executor detects it and sends a killing event.
        State.Type exitState = executionState(runContext);
        return ExecutionTerminator.terminate(execution, execution.findLastNotTerminated().orElse(null), exitState);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution) throws IllegalVariableEvaluationException {
        return Optional.of(executionState(runContext));
    }

    private State.Type executionState(RunContext runContext) throws IllegalVariableEvaluationException {
        // the executor reuses the task instance of its cached flow for every execution of the flow,
        // so the rendering cache of the property must be skipped to render with the current execution context
        return switch (runContext.render(this.state.skipCache()).as(ExitState.class).orElseThrow()) {
            case ExitState.SUCCESS -> State.Type.SUCCESS;
            case WARNING -> State.Type.WARNING;
            case KILLED -> State.Type.KILLED;
            case FAILED -> State.Type.FAILED;
            case CANCELLED -> State.Type.CANCELLED;
        };
    }

    public enum ExitState {
        SUCCESS,
        WARNING,
        KILLED,
        FAILED,
        CANCELLED
    }
}
