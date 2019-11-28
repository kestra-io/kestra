package org.floworc.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.runners.RunnerUtils;
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
public class Flow extends Task implements RunnableTask {

    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    private Integer revision;

    private Map<String, String> inputs;

    @SuppressWarnings("unchecked")
    @Override
    public RunOutput run(RunContext runContext) throws Exception {
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


        org.floworc.core.models.flows.Flow flow = flowRepository.findById(
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

        return RunOutput.builder()
            .outputs(ImmutableMap.of("executionId", execution.getId()))
            .build();
    }
}
