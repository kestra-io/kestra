package io.kestra.core.tasks.log;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
    }
)
public class Fetch extends Task implements RunnableTask<Fetch.Output> {
    @Schema(
        title = "Filter for a specific execution.",
        description = "If not set, the task will use the ID of the current execution."
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

    @SuppressWarnings("unchecked")
    @Override
    public Output run(RunContext runContext) throws Exception {
        String executionId = this.executionId != null ? runContext.render(this.executionId) : (String) new HashMap<>((Map<String, Object>) runContext.getVariables().get("execution")).get("id");
        LogRepositoryInterface logRepository = runContext.getApplicationContext().getBean(LogRepositoryInterface.class);

        File tempFile = runContext.tempFile(".ion").toFile();
        AtomicLong count = new AtomicLong();

        Map<String, String> flowVars = (Map<String, String>) runContext.getVariables().get("flow");
        String tenantId = flowVars.get("tenantId");

        try (OutputStream output = new FileOutputStream(tempFile)) {
            if (this.tasksId != null) {
                for (String taskId : tasksId) {
                    logRepository
                        .findByExecutionIdAndTaskId(tenantId, executionId, taskId, level)
                        .forEach(throwConsumer(log -> {
                            count.incrementAndGet();
                            FileSerde.write(output, log);
                        }));
                }
            } else {
                logRepository
                    .findByExecutionId(tenantId, executionId, level)
                    .forEach(throwConsumer(log -> {
                        count.incrementAndGet();
                        FileSerde.write(output, log);
                    }));
            }
        }

        return Output
            .builder()
            .uri(runContext.putTempFile(tempFile))
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
