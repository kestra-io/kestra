package org.kestra.core.tasks.flows;

import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
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
    private String namespace;

    @NotNull
    private String flowId;

    private Integer revision;

    private Map<String, String> inputs;

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

        executionQueue.emit(execution);

        logger.debug(
            "Create new execution for flow {}.{} with id {}",
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId()
        );

        return Output.builder()
            .executionId(execution.getId())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        private String executionId;
    }
}
