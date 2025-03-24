package io.kestra.plugin.core.execution;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.FlowService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge executions, logs, metrics, and storage files.",
    description = "This task can be used to purge flow executions data for all flows, for a specific namespace, or for a specific flow."
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all flow execution data for flows that ended more than one month ago.",
            code = {
                "endDate: \"{{ now() | dateAdd(-1, 'MONTHS') }}\"",
                "states: ",
                " - KILLED",
                " - FAILED",
                " - WARNING",
                " - SUCCESS"
            }
        )
    },
    aliases = {"io.kestra.core.tasks.storages.Purge", "io.kestra.plugin.core.storage.Purge"}
)
public class PurgeExecutions extends Task implements RunnableTask<PurgeExecutions.Output> {
    @Schema(
        title = "Namespace whose flows need to be purged, or namespace of the flow that needs to be purged.",
        description = "If `flowId` isn't provided, this is a namespace prefix, else the namespace of the flow."
    )
    private Property<String> namespace;

    @Schema(
        title = "The flow ID to be purged.",
        description = "You need to provide the `namespace` properties if you want to purge a flow."
    )
    private Property<String> flowId;

    @Schema(
        title = "The minimum date to be purged.",
        description = "All data of flows executed after this date will be purged."
    )
    private Property<String> startDate;

    @Schema(
        title = "The maximum date to be purged.",
        description = "All data of flows executed before this date will be purged."
    )
    @NotNull
    private Property<String> endDate;

    @Schema(
        title = "The state of the executions to be purged.",
        description = "If not set, executions for any states will be purged."
    )
    private Property<List<State.Type>> states;

    @Schema(
        title = "Whether to purge executions."
    )
    @Builder.Default
    private Property<Boolean> purgeExecution = Property.of(true);

    @Schema(
        title = "Whether to purge execution's logs.",
        description = """
            This will only purge logs from executions not from triggers, and it will do it execution by execution.
            The `io.kestra.plugin.core.log.PurgeLogs` task is a better fit to purge logs as it will purge logs in bulk, and will also purge logs not tied to an execution like trigger logs."""
    )
    @Builder.Default
    private Property<Boolean> purgeLog = Property.of(true);

    @Schema(
        title = "Whether to purge execution's metrics."
    )
    @Builder.Default
    private Property<Boolean> purgeMetric = Property.of(true);

    @Schema(
        title = "Whether to purge execution's files from the Kestra's internal storage."
    )
    @Builder.Default
    private Property<Boolean> purgeStorage = Property.of(true);

    @Override
    public PurgeExecutions.Output run(RunContext runContext) throws Exception {
        ExecutionService executionService = ((DefaultRunContext)runContext).getApplicationContext().getBean(ExecutionService.class);
        FlowService flowService = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowService.class);

        // validate that this namespace is authorized on the target namespace / all namespaces
        var flowInfo = runContext.flowInfo();
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElse(null);
        if (renderedNamespace == null){
            flowService.checkAllowedAllNamespaces(flowInfo.tenantId(), flowInfo.tenantId(), flowInfo.namespace());
        } else if (!renderedNamespace.equals(flowInfo.namespace())) {
            flowService.checkAllowedNamespace(flowInfo.tenantId(), renderedNamespace, flowInfo.tenantId(), flowInfo.namespace());
        }

        ExecutionService.PurgeResult purgeResult = executionService.purge(
            runContext.render(this.purgeExecution).as(Boolean.class).orElseThrow(),
            runContext.render(this.purgeLog).as(Boolean.class).orElseThrow(),
            runContext.render(this.purgeMetric).as(Boolean.class).orElseThrow(),
            runContext.render(this.purgeStorage).as(Boolean.class).orElseThrow(),
            flowInfo.tenantId(),
            renderedNamespace,
            runContext.render(flowId).as(String.class).orElse(null),
            startDate != null ? ZonedDateTime.parse(runContext.render(startDate).as(String.class).orElseThrow()) : null,
            ZonedDateTime.parse(runContext.render(endDate).as(String.class).orElseThrow()),
            this.states == null ? null : runContext.render(this.states).asList(State.Type.class)
        );

        return Output.builder()
            .executionsCount(purgeResult.getExecutionsCount())
            .logsCount(purgeResult.getLogsCount())
            .storagesCount(purgeResult.getStoragesCount())
            .metricsCount(purgeResult.getMetricsCount())
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The count of deleted executions."
        )
        private int executionsCount;

        @Schema(
            title = "The count of deleted logs."
        )
        private int logsCount;

        @Schema(
            title = "The count of deleted storage files."
        )
        private int storagesCount;

        @Schema(
            title = "The count of deleted metrics."
        )
        private int metricsCount;
    }
}
