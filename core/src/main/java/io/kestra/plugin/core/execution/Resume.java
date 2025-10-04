package io.kestra.plugin.core.execution;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutionUpdatableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionService;
import io.kestra.plugin.core.flow.Pause;
import io.micronaut.context.ApplicationContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Resume a paused execution.",
    description = "By default, the task assumes that you want to resume the current `executionId`. If you want to programmatically resume an execution of another flow, make sure to define the `executionId`, `flowId`, and `namespace` properties explicitly. Using the `inputs` property, you can additionally pass custom `onResume` input values to the execution."
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
public class Resume extends Task implements ExecutionUpdatableTask {
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

            If `executionId` is not set and `namespace` and `flowId` properties are set, Kestra will look for a paused execution for that corresponding flow.

            If `executionId` is not set, the task will use the ID of the current execution."""
    )
    private Property<String> executionId;

    @Schema(
        title = "Inputs to be passed to the execution when it's resumed"
    )
    private Property<Map<String, Object>> inputs;

    @Override
    public Execution update(Execution execution, RunContext runContext) throws Exception {
        var executionInfo = PluginUtilsService.executionFromTaskParameters(
            runContext,
            runContext.render(this.namespace).as(String.class).orElse(null),
            runContext.render(this.flowId).as(String.class).orElse(null),
            runContext.render(this.executionId).as(String.class).orElse(null)
        );

        ApplicationContext applicationContext = ((DefaultRunContext)runContext).getApplicationContext();
        ExecutionService executionService = applicationContext.getBean(ExecutionService.class);
        ExecutionRepositoryInterface executionRepository = applicationContext.getBean(ExecutionRepositoryInterface.class);
        FlowMetaStoreInterface flowExecutor = applicationContext.getBean(FlowMetaStoreInterface.class);

        Execution targetExecution = executionRepository.findById(executionInfo.tenantId(), executionInfo.id())
            .orElseThrow(() -> new IllegalArgumentException("No execution found for execution id " + executionInfo.id()));
        FlowInterface flow = flowExecutor.findByExecution(targetExecution).orElseThrow(() -> new IllegalArgumentException("Flow not found for execution ID " + executionInfo.id()));

        Map<String, Object> renderedInputs = runContext.render(this.inputs).asMap(String.class, Object.class);
        renderedInputs = !renderedInputs.isEmpty() ? renderedInputs : null;
        
        Execution resumed = executionService.resume(targetExecution, flow, State.Type.RUNNING, renderedInputs, Pause.Resumed.now());
        
        Map<String, Object> variables = new HashMap<>(execution.getVariables() != null ? execution.getVariables() : Map.of());
        variables.put("resumedExecution", Map.of(
            "id", targetExecution.getId(),
            "namespace", targetExecution.getNamespace(),
            "flowId", targetExecution.getFlowId()
        ));
        
        return execution.withVariables(variables);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution) {
        return Optional.of(State.Type.SUCCESS);
    }
}
