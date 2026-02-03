package io.kestra.plugin.core.log;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch execution logs to a file (deprecated).",
    description = """
        Deprecated; use `io.kestra.plugin.kestra.logs.Fetch`.

        Streams logs for a given execution (current by default) into an ION file in internal storage. You can filter by task ids and minimum log level. Execution can be targeted via `executionId`/`namespace`/`flowId` with ACL checks."""
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch ERROR level logs from the same execution.",
            full = true,
            code = """
                id: fetch_logs
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World! 🚀

                  - id: error_message
                    type: io.kestra.plugin.core.log.Log
                    level: ERROR
                    message: This is an error message

                  - id: fetch
                    type: io.kestra.plugin.core.log.Fetch
                    executionId: "{{ execution.id }}"
                    level: ERROR
            """
        ),
        @Example(
            title = "Fetch INFO level logs from the `hello` task from the same execution.",
            full = true,
            code = """
                id: fetch_logs
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World! 🚀

                  - id: error_message
                    type: io.kestra.plugin.core.log.Log
                    level: ERROR
                    message: This is an error message

                  - id: fetch
                    type: io.kestra.plugin.core.log.Fetch
                    level: INFO
                    tasksId:
                      - hello
            """
        )
    },
    aliases = "io.kestra.core.tasks.log.Fetch"
)
@Deprecated(since = "1.3", forRemoval = true)
public class Fetch extends Task implements RunnableTask<Fetch.Output> {
    @Schema(
        title = "Filter for a specific namespace in case `executionId` is set."
    )
    private Property<String> namespace;

    @Schema(
        title = "Filter for a specific flow identifier in case `executionId` is set."
    )
    private Property<String> flowId;

    @Schema(
        title = "Filter for a specific execution.",
        description = """
            If not set, the task will use the ID of the current execution.
            If set, it will try to locate the execution on the current flow unless the `namespace` and `flowId` properties are set."""
    )
    private Property<String> executionId;

    @Schema(
        title = "Filter for one or more task(s)."
    )
    private Property<List<String>> tasksId;

    @Schema(
        title = "The lowest log level that you want to fetch"
    )
    @Builder.Default
    private Property<Level> level = Property.ofValue(Level.INFO);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var executionInfo = PluginUtilsService.executionFromTaskParameters(
            runContext,
            runContext.render(this.namespace).as(String.class).orElse(null),
            runContext.render(this.flowId).as(String.class).orElse(null),
            runContext.render(this.executionId).as(String.class).orElse(null)
        );

        LogRepositoryInterface logRepository = ((DefaultRunContext)runContext).getApplicationContext().getBean(LogRepositoryInterface.class);

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        AtomicLong count = new AtomicLong();

        try (OutputStream output = new FileOutputStream(tempFile)) {
            var renderedTaskId = runContext.render(this.tasksId).asList(String.class);
            var logLevel = runContext.render(this.level).as(Level.class).orElseThrow();
            if (!renderedTaskId.isEmpty()) {
                for (String taskId : renderedTaskId) {
                    logRepository
                        .findByExecutionIdAndTaskId(executionInfo.tenantId(), executionInfo.namespace(), executionInfo.flowId(), executionInfo.id(), taskId, logLevel)
                        .forEach(throwConsumer(log -> {
                            count.incrementAndGet();
                            FileSerde.write(output, log);
                        }));
                }
            } else {
                logRepository
                    .findByExecutionId(executionInfo.tenantId(), executionInfo.namespace(), executionInfo.flowId(), executionInfo.id(), logLevel)
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
            title = "The number of rows fetched"
        )
        private Long size;

        @Schema(
            title = "Internal storage URI of stored results",
            description = "Stored as Amazon ION file in a row per row format."
        )
        private URI uri;
    }
}
