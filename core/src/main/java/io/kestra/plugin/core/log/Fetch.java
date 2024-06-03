package io.kestra.plugin.core.log;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch execution logs and store them in a file.",
    description = "This task is useful to automate moving logs between various systems and environments."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "level: INFO",
                "executionId: \"{{ trigger.executionId }}\""
            }
        ),
        @Example(
            code = {
                "level: WARN",
                "executionId: \"{{ execution.id }}\"",
                "tasksId: ",
                "  - \"previous_task_id\""
            }
        )
    },
    aliases = "io.kestra.core.tasks.log.Fetch"
)
public class Fetch extends Task implements RunnableTask<Fetch.Output> {
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
        title = "Filter for one or more task(s)."
    )
    @PluginProperty
    private Collection<String> tasksId;

    @Schema(
        title = "The lowest log level that you want to fetch."
    )
    @Builder.Default
    @PluginProperty
    private Level level = Level.INFO;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var executionInfo = PluginUtilsService.executionFromTaskParameters(runContext, this.namespace, this.flowId, this.executionId);

        LogRepositoryInterface logRepository = runContext.getApplicationContext().getBean(LogRepositoryInterface.class);

        File tempFile = runContext.tempFile(".ion").toFile();
        AtomicLong count = new AtomicLong();

        try (OutputStream output = new FileOutputStream(tempFile)) {
            if (this.tasksId != null) {
                for (String taskId : tasksId) {
                    logRepository
                        .findByExecutionIdAndTaskId(executionInfo.tenantId(), executionInfo.namespace(), executionInfo.flowId(), executionInfo.id(), taskId, level)
                        .forEach(throwConsumer(log -> {
                            count.incrementAndGet();
                            FileSerde.write(output, log);
                        }));
                }
            } else {
                logRepository
                    .findByExecutionId(executionInfo.tenantId(), executionInfo.namespace(), executionInfo.flowId(), executionInfo.id(), level)
                    .forEach(throwConsumer(log -> {
                        count.incrementAndGet();
                        FileSerde.write(output, log);
                    }));
            }
        }

        return Output
            .builder()
            .uri(runContext.storage().putFile(tempFile))
            .size(count.get())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The number of rows fetched."
        )
        private Long size;

        @Schema(
            title = "Internal storage URI of stored results.",
            description = "Stored as Amazon ION file in a row per row format."
        )
        private URI uri;
    }
}
