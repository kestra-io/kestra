package io.kestra.plugin.core.execution;

import com.ctc.wstx.util.PrefixedName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.micronaut.context.ApplicationContext;
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
    title = "Resume a paused execution. By default, the task assumes that you want to resume the current `executionId`. If you want to programmatically resume an execution of another flow, make sure to define the `executionId`, `flowId`, and `namespace` properties explicitly. Using the `inputs` property, you can additionally pass custom `onResume` input values to the execution."
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
        title = "Filter for a specific namespace in case `executionId` is set. In case you wonder why `executionId` is not enough — we require specifying the namespace to make permissions explicit. The Enterprise Edition of Kestra allows you to resume executions from another namespaces only if the permissions allow it. Check the [Allowed Namespaces](https://kestra.io/docs/enterprise/allowed-namespaces) documentation for more details."
    )
    private Property<String> namespace;

    @Schema(
        title = "Filter for a specific flow identifier in case `executionId` is set."
    )
    private Property<String> flowId;

    @Schema(
        title = "Filter for a specific execution.",
        description = """
            If you explicitly define an `executionId`, Kestra will use that specific ID.

            If another `namespace` and `flowId` properties are set, Kestra will look for a paused execution for that corresponding flow.

            If `executionId` is not set, the task will use the ID of the current execution."""
    )
    private Property<String> executionId;

    @Schema(
        title = "Inputs to be passed to the execution when it's resumed."
    )
    private Property<Map<String, Object>> inputs;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var executionInfo = PluginUtilsService.executionFromTaskParameters(
            runContext,
            runContext.render(this.namespace).as(String.class).orElse(null),
            runContext.render(this.flowId).as(String.class).orElse(null),
            runContext.render(this.executionId).as(String.class).orElse(null)
        );

        ApplicationContext applicationContext = ((DefaultRunContext)runContext).getApplicationContext();
        ExecutionService executionService = applicationContext.getBean(ExecutionService.class);
        ExecutionRepositoryInterface executionRepository = applicationContext.getBean(ExecutionRepositoryInterface.class);
        FlowExecutorInterface flowExecutor = applicationContext.getBean(FlowExecutorInterface.class);
        QueueInterface<Execution> executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));

        Execution execution = executionRepository.findById(executionInfo.tenantId(), executionInfo.id())
            .orElseThrow(() -> new IllegalArgumentException("No execution found for execution id " + executionInfo.id()));
        Flow flow = flowExecutor.findByExecution(execution).orElseThrow(() -> new IllegalArgumentException("Flow not found for execution id " + executionInfo.id()));

        Map<String, Object> renderedInputs = runContext.render(this.inputs).asMap(String.class, Object.class);
        renderedInputs = !renderedInputs.isEmpty() ? renderedInputs : null;
        Execution resumed = executionService.resume(execution, flow, State.Type.RUNNING, renderedInputs);
        executionQueue.emit(resumed);

        return null;
    }
}
