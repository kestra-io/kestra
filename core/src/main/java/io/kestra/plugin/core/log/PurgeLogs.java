package io.kestra.plugin.core.log;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.event.Level;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.ExecutionLogService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge execution and trigger logs.",
    description = """
        Deletes logs in bulk by namespace/flow/execution filters and optional level/date ranges. Requires namespace authorization when targeting other namespaces.

        For performance, use this instead of per-execution deletions; consider keeping ERROR logs by filtering `logLevels`."""
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all logs that has been created more than one month ago.",
            full = true,
            code = """
                    id: purge
                    namespace: system

                    tasks:
                      - id: purge_logs
                        type: io.kestra.plugin.core.log.PurgeLogs
                        endDate: "{{ now() | dateAdd(-1, 'MONTHS') }}"
                """
        ),
        @Example(
            title = "Purge all logs that has been created more than one month ago, but keep error logs.",
            full = true,
            code = """
                    id: purge
                    namespace: system

                    tasks:
                      - id: purge
                        type: io.kestra.plugin.core.log.PurgeLogs
                        endDate: "{{ now() | dateAdd(-1, 'MONTHS') }}"
                        logLevels:
                          - TRACE
                          - DEBUG
                          - INFO
                          - WARN
                """
        )
    }
)
public class PurgeLogs extends Task implements RunnableTask<PurgeLogs.Output>, SystemTask {
    @Schema(
        title = "Namespace of logs that need to be purged",
        description = "If `flowId` isn't provided, this is a namespace prefix, else the namespace of the flow."
    )
    private Property<String> namespace;

    @Schema(
        title = "The flow ID of the logs to be purged",
        description = "You need to provide the `namespace` property if you want to purge flow logs."
    )
    private Property<String> flowId;

    @Schema(
        title = "The Execution ID of the logs to be purged"
    )
    private Property<String> executionId;

    @Schema(
        title = "The levels of the logs to be purged",
        description = "If not set, log for all levels will be purged."
    )
    private Property<List<Level>> logLevels;

    @Schema(
        title = "The minimum date to be purged",
        description = "All logs after this date will be purged."
    )
    private Property<String> startDate;

    @Schema(
        title = "The maximum date to be purged",
        description = "All logs before this date will be purged."
    )
    @NotNull
    private Property<String> endDate;

    @Schema(
        title = "Whether to purge execution logs",
        description = "If set to `true`, logs attached to an execution will be purged. Default is `true`."
    )
    @Builder.Default
    private Property<Boolean> purgeExecutionLogs = Property.ofValue(true);

    @Schema(
        title = "Whether to purge non-execution logs",
        description = "If set to `true`, logs not attached to an execution (e.g. trigger logs) will be purged. Default is `true`."
    )
    @Builder.Default
    private Property<Boolean> purgeNonExecutionLogs = Property.ofValue(true);

    @Schema(
        title = "The number of log rows deleted per batch",
        description = "Only applies on MySQL. When set, deletion runs in batches of this size using `DELETE ... LIMIT n` to stay within `group_replication_transaction_size_limit`. When not set, all matching rows are deleted in a single transaction."
    )
    private Property<Integer> batchSize;

    @Override
    public Output run(RunContext runContext) throws Exception {
        ExecutionLogService logService = ((DefaultRunContext) runContext).services().additionalService(ExecutionLogService.class);

        // validate that this namespace is authorized on the target namespace / all namespaces
        var flowInfo = runContext.flowInfo();
        if (namespace == null) {
            runContext.acl().allowAllNamespaces().check();
        } else if (!flowInfo.namespace().equals(runContext.render(namespace).as(String.class).orElse(null))) {
            runContext.acl().allowNamespace(runContext.render(namespace).as(String.class).orElse(null)).check();
        }

        var logLevelsRendered = runContext.render(this.logLevels).asList(Level.class);
        var renderedDate = runContext.render(startDate).as(String.class).orElse(null);
        boolean execLogs = runContext.render(purgeExecutionLogs).as(Boolean.class).orElse(true);
        boolean nonExecLogs = runContext.render(purgeNonExecutionLogs).as(Boolean.class).orElse(true);

        var purgeResult = logService.purge(
            flowInfo.tenantId(),
            runContext.render(namespace).as(String.class).orElse(null),
            runContext.render(flowId).as(String.class).orElse(null),
            runContext.render(executionId).as(String.class).orElse(null),
            logLevelsRendered.isEmpty() ? null : logLevelsRendered,
            renderedDate != null ? ZonedDateTime.parse(renderedDate) : null,
            ZonedDateTime.parse(runContext.render(endDate).as(String.class).orElseThrow()),
            execLogs,
            nonExecLogs,
            runContext.render(this.batchSize).as(Integer.class).orElse(null)
        );

        return Output.builder()
            .count(purgeResult.executionLogsDeleted() + purgeResult.nonExecutionLogsDeleted())
            .executionLogsCount(purgeResult.executionLogsDeleted())
            .nonExecutionLogsCount(purgeResult.nonExecutionLogsDeleted())
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The total count of deleted logs"
        )
        private int count;

        @Schema(
            title = "The count of deleted execution logs"
        )
        private int executionLogsCount;

        @Schema(
            title = "The count of deleted non-execution logs"
        )
        private int nonExecutionLogsCount;
    }
}
