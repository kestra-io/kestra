package org.kestra.core.tasks.flows;

import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.annotations.OutputProperty;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunnerUtils;
import org.slf4j.Logger;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Flow extends Task implements RunnableTask<Flow.Output> {
    @NotNull
    @InputProperty(
        description = "The namespace of the flow to trigger",
        dynamic = true
    )
    private String namespace;

    @NotNull
    @InputProperty(
        description = "The flowId to trigger",
        dynamic = true
    )
    private String flowId;

    @InputProperty(
        description = "The revision of the flow you want to trigger",
        body = "By default, we trigger the last version.",
        dynamic = false
    )
    private Integer revision;

    @InputProperty(
        description = "The input to pass to the triggered flow",
        dynamic = true
    )
    private Map<String, String> inputs;

    @InputProperty(
        description = "Wait the end of the execution.",
        body = "By default, we don't wait till the end of the flow, if you set to true, we wait the end of the trigger flow before continue this one.",
        dynamic = false
    )
    private Boolean wait;

    @SuppressWarnings("unchecked")
    @Override
    public Flow.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());
        RunnerUtils runnerUtils = runContext.getApplicationContext().getBean(RunnerUtils.class);
        FlowRepositoryInterface flowRepository = runContext.getApplicationContext().getBean(FlowRepositoryInterface.class);
        QueueInterface<Execution> executionQueue = (QueueInterface<Execution>) runContext.getApplicationContext().getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED)
        );

        Map<String, String> inputs = new HashMap<>();
        if (this.inputs != null) {
            for (Map.Entry<String, String> entry: this.inputs.entrySet()) {
                inputs.put(entry.getKey(), runContext.render(entry.getValue()));
            }
        }

        org.kestra.core.models.flows.Flow flow = flowRepository.findById(
            runContext.render(this.namespace),
            runContext.render(this.flowId),
            this.revision != null ? Optional.of(this.revision) : Optional.empty()
        ).orElseThrow();

        Execution execution = runnerUtils.newExecution(
            flow,
            (f, e) -> runnerUtils.typedInputs(f, e, inputs)
        );

        Output.OutputBuilder outputBuilder = Output.builder()
            .executionId(execution.getId());

        logger.debug(
            "Create new execution for flow {}.{} with id {}",
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId()
        );

        if (!wait) {
            executionQueue.emit(execution);
        } else {
            Execution ended = runnerUtils.awaitExecution(
                runnerUtils.isTerminatedExecution(execution, flow),
                () -> {
                    executionQueue.emit(execution);
                },
                null
            );

            outputBuilder.state(ended.getState().getCurrent());
        }

        return outputBuilder
            .build();
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        @OutputProperty(
            description = "The id of the execution trigger."
        )
        private String executionId;

        @OutputProperty(
            description = "The state of the execution trigger.",
            body = "Only available if the execution is waited with `wait` options"
        )
        private State.Type state;
    }
}
