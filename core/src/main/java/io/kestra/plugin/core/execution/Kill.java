package io.kestra.plugin.core.execution;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
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
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Conditionally terminate an execution.",
    description = "This task will kill the current execution and optionally propagate the kill to child executions."
)
@Plugin(
    examples = {
        @Example(
            title = "Kill execution conditionally",
            full = true,
            code = """
                id: conditional-kill-flow
                namespace: company.team

                inputs:
                  - id: shouldKill
                    type: boolean
                    defaults: false

                tasks:
                  - id: subflow
                    type: io.kestra.plugin.core.flow.subflow
                    namespace: demo
                    flowId: child
                    wait: false
                   \s
                  - id: kill
                    type: io.kestra.plugin.core.execution.kill
                    runIf: "{{ inputs.shouldKill == ture }} "
                    propagateKill: true
               \s"""
        ),
        @Example(
            title = "Kill execution based on condition",
            full = true,
            code = """
                id: kill-task
                type: io.kestra.plugin.core.execution.kill
                runIf: "{{ outputs.validation.body.value == false }}"
                propagateKill: false
                """
        )
    }
)
public class Kill extends Task implements ExecutionUpdatableTask {
    @Schema(
        title = "Propagate kill to child executions",
        description = "Whether to also kill the child execution(subflow) when this execution is killed."
    )
    @Builder.Default
    private Property<Boolean> propagateKill = Property.ofValue(false);

    @Override
    public Execution update(Execution execution, RunContext runContext) throws Exception {
        boolean shouldPropagateKill = runContext.render(this.propagateKill).as(Boolean.class).orElse(false);

        QueueInterface<ExecutionKilled> killQueue = ((DefaultRunContext) runContext).getApplicationContext()
            .getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.KILL_NAMED));
        killQueue.emit(ExecutionKilledExecution
            .builder()
            .state(ExecutionKilled.State.REQUESTED)
            .executionId(execution.getId())
            .isOnKillCascade(shouldPropagateKill)
            .tenantId(execution.getTenantId())
            .build()
        );
        return execution.withState(State.Type.KILLED);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution) throws IllegalVariableEvaluationException {
        return Optional.of(State.Type.KILLED);
    }
}