package io.kestra.plugin.core.execution;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.tasks.PluginUtilsService;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Resume a paused execution."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "executionId: \"{{ trigger.executionId }}\""
            }
        )
    }
)
public class Resume  extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Filter for a specific namespace in case `executionId` is set."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @Schema(
        title = "Filter for a specific flow identifier in case `executionId` is set."
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "Filter for a specific execution.",
        description = """
            If not set, the task will use the ID of the current execution.
            If set, it will try to locate the execution on the current flow unless the `namespace` and `flowId` properties are set."""
    )
    @PluginProperty(dynamic = true)
    private String executionId;

    @Schema(
        title = "Inputs to be passed to the execution when it's resumed."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        System.out.println("resume");
        var executionInfo = PluginUtilsService.executionFromTaskParameters(runContext, this.namespace, this.flowId, this.executionId);

        ExecutionService executionService = runContext.getApplicationContext().getBean(ExecutionService.class);
        ExecutionRepositoryInterface executionRepository = runContext.getApplicationContext().getBean(ExecutionRepositoryInterface.class);
        FlowExecutorInterface flowExecutor = runContext.getApplicationContext().getBean(FlowExecutorInterface.class);
        QueueInterface<Execution> executionQueue = runContext.getApplicationContext().getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));

        Execution execution = executionRepository.findById(executionInfo.tenantId(), executionInfo.id())
            .orElseThrow(() -> new IllegalArgumentException("No execution found for execution id " + executionInfo.id()));
        Flow flow = flowExecutor.findByExecution(execution).orElseThrow(() -> new IllegalArgumentException("Flow not found for execution id " + executionInfo.id()));
        Map<String, Object> renderedInputs = inputs != null ? runContext.render(inputs) : null;
        Execution resumed = executionService.resume(execution, flow, State.Type.RUNNING, renderedInputs, null);
        executionQueue.emit(resumed);

        return null;
    }
}
