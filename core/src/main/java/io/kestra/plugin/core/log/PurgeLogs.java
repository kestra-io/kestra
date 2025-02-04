package io.kestra.plugin.core.log;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.LogService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge flow execution and trigger logs.",
    description = "This task can be used to purge flow execution and trigger logs for all flows, for a specific namespace, or for a specific flow."
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all logs that has been created more than one month ago.",
            code = {
                "endDate: \"{{ now() | dateAdd(-1, 'MONTHS') }}\""
            }
        ),
        @Example(
            title = "Purge all logs that has been created more than one month ago, but keep error logs.",
            code = {
                "endDate: \"{{ now() | dateAdd(-1, 'MONTHS') }}\"",
                "logLevels:",
                "  - TRACE",
                "  - DEBUG",
                "  - INFO",
                "  - WARN",
            }
        )
    }
)
public class PurgeLogs extends Task implements RunnableTask<PurgeLogs.Output> {
    @Schema(
        title = "Namespace whose logs need to be purged, or namespace of the logs that needs to be purged.",
        description = "If `flowId` isn't provided, this is a namespace prefix, else the namespace of the flow."
    )
    private Property<String> namespace;

    @Schema(
        title = "The flow ID of the logs to be purged.",
        description = "You need to provide the `namespace` properties if you want to purge a flow logs."
    )
    private Property<String> flowId;

    @Schema(
        title = "The levels of the logs to be purged.",
        description = "If not set, log for any levels will be purged."
    )
    private Property<List<Level>> logLevels;

    @Schema(
        title = "The minimum date to be purged.",
        description = "All logs after this date will be purged."
    )
    private Property<String> startDate;

    @Schema(
        title = "The maximum date to be purged.",
        description = "All logs before this date will be purged."
    )
    @NotNull
    private Property<String> endDate;

    @Override
    public Output run(RunContext runContext) throws Exception {
        LogService logService = ((DefaultRunContext)runContext).getApplicationContext().getBean(LogService.class);
        FlowService flowService = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowService.class);

        // validate that this namespace is authorized on the target namespace / all namespaces
        var flowInfo = runContext.flowInfo();
        if (namespace == null){
            flowService.checkAllowedAllNamespaces(flowInfo.tenantId(), flowInfo.tenantId(), flowInfo.namespace());
        } else if (!flowInfo.namespace().equals(runContext.render(namespace).as(String.class).orElse(null))) {
            flowService.checkAllowedNamespace(flowInfo.tenantId(), runContext.render(namespace).as(String.class).orElse(null), flowInfo.tenantId(), flowInfo.namespace());
        }

        var logLevelsRendered = runContext.render(this.logLevels).asList(String.class);
        var renderedDate = runContext.render(startDate).as(String.class).orElse(null);
        int deleted = logService.purge(
            flowInfo.tenantId(),
            runContext.render(namespace).as(String.class).orElse(null),
            runContext.render(flowId).as(String.class).orElse(null),
            logLevelsRendered.isEmpty() ? null : logLevelsRendered,
            renderedDate != null ? ZonedDateTime.parse(renderedDate) : null,
            ZonedDateTime.parse(runContext.render(endDate).as(String.class).orElseThrow())
        );

        return Output.builder().count(deleted).build();
    }


    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The count of deleted logs."
        )
        private int count;
    }
}
