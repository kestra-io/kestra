package io.kestra.core.tasks.storages;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionService;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Purge execution, logs or storage files."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "endDate: \"{{ now() | dateAdd(-1, 'MONTH') }}\"",
                "states: ",
                " - KILLED",
                " - FAILED",
                " - WARNING",
                " - SUCCESS"
            }
        )
    }
)
public class Purge extends Task implements RunnableTask<Purge.Output> {
    @Schema(
        title = "Namespace to purge or namespace for a flow",
        description = "If `flowId` isn't provide, this is a namespace prefix, else the namespace of flow"
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @Schema(
        title = "The flow id to purge",
        description = "You need to provide the `namespace` properties if you want to purge a flow"
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The max date to purge",
        description = "All date after this date will be purged."
    )
    @PluginProperty(dynamic = true)
    private String endDate;

    @Schema(
        title = "The state of the execution that can be purged."
    )
    @PluginProperty(dynamic = false)
    private List<State.Type> states;

    @Schema(
        title = "Purge execution from repository"
    )
    @PluginProperty(dynamic = false)
    @Builder.Default
    private boolean purgeExecution = true;

    @Schema(
        title = "Purge log from repository"
    )
    @PluginProperty(dynamic = false)
    @Builder.Default
    private boolean purgeLog = true;

    @Schema(
        title = "Purge file from internal storage"
    )
    @PluginProperty(dynamic = false)
    @Builder.Default
    private boolean purgeStorage = true;

    @Override
    public Purge.Output run(RunContext runContext) throws Exception {
        ExecutionService executionService = runContext.getApplicationContext().getBean(ExecutionService.class);

        ExecutionService.PurgeResult purgeResult = executionService.purge(
            purgeExecution,
            purgeLog,
            purgeStorage,
            namespace,
            flowId,
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
            title = "The count of executions deleted"
        )
        private int executionsCount;

        @Schema(
            title = "The count of logs deleted"
        )
        private int logsCount;

        @Schema(
            title = "The count of storage deleted"
        )
        private int storagesCount;
    }
}
