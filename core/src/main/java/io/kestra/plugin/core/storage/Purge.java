package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.FlowService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

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
    aliases = "io.kestra.core.tasks.storages.Purge"
)
public class Purge extends Task implements RunnableTask<Purge.Output> {
    @Schema(
        title = "Namespace whose flows need to be purged, or namespace of the flow that needs to be purged.",
        description = "If `flowId` isn't provided, this is a namespace prefix, else the namespace of the flow."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @Schema(
        title = "The flow ID to be purged.",
        description = "You need to provide the `namespace` properties if you want to purge a flow."
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The maximum date to be purged.",
        description = "All data of flows executed before this date will be purged."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String endDate;

    @Schema(
        title = "The state of the execution that can be purged."
    )
    @PluginProperty
    private List<State.Type> states;

    @Schema(
        title = "Whether to purge executions from the repository."
    )
    @PluginProperty
    @Builder.Default
    private boolean purgeExecution = true;

    @Schema(
        title = "Whether to purge logs from the repository."
    )
    @PluginProperty
    @Builder.Default
    private boolean purgeLog = true;

    @Schema(
        title = "Whether to purge metrics from the repository."
    )
    @PluginProperty
    @Builder.Default
    private boolean purgeMetric = true;

    @Schema(
        title = "Whether to purge files from the Kestra's internal storage."
    )
    @PluginProperty
    @Builder.Default
    private boolean purgeStorage = true;

    @Override
    public Purge.Output run(RunContext runContext) throws Exception {
        ExecutionService executionService = runContext.getApplicationContext().getBean(ExecutionService.class);
        FlowService flowService = runContext.getApplicationContext().getBean(FlowService.class);

        // validate that this namespace is authorized on the target namespace / all namespaces
        var flowInfo = runContext.flowInfo();
        if (namespace == null){
            flowService.checkAllowedAllNamespaces(flowInfo.tenantId(), flowInfo.tenantId(), flowInfo.namespace());
        } else if (!runContext.render(namespace).equals(flowInfo.namespace())) {
            flowService.checkAllowedNamespace(flowInfo.tenantId(), runContext.render(namespace), flowInfo.tenantId(), flowInfo.namespace());
        }

        ExecutionService.PurgeResult purgeResult = executionService.purge(
            purgeExecution,
            purgeLog,
            purgeMetric,
            purgeStorage,
            flowInfo.tenantId(),
            runContext.render(namespace),
            runContext.render(flowId),
            ZonedDateTime.parse(runContext.render(endDate)),
            states
        );

        return Output.builder()
            .executionsCount(purgeResult.getExecutionsCount())
            .logsCount(purgeResult.getLogsCount())
            .storagesCount(purgeResult.getStoragesCount())
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The count of executions deleted."
        )
        private int executionsCount;

        @Schema(
            title = "The count of logs deleted."
        )
        private int logsCount;

        @Schema(
            title = "The count of storage deleted."
        )
        private int storagesCount;
    }
}
