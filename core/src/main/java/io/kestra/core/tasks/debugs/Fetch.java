package io.kestra.core.tasks.debugs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.LogEntry;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Output execution logs in a file.",
    description = "This task is useful to propagate your logs."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "level: ERROR",
            }
        ),
        @Example(
            code = {
                "level: WARN",
                "tasksId: " +
                "   - \"previous-task-id\""
            }
        )
    }
)
public class Fetch extends Task implements RunnableTask<Fetch.Output> {

    @Schema(
        title = "Filter on specific task(s)"
    )
    @PluginProperty
    private Collection<String> tasksId;

    @Schema(
        title = "Minimum log level you want to fetch"
    )
    @Builder.Default
    @PluginProperty
    private Level level = Level.INFO;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String executionId = (String) new HashMap<>((Map<String, Object>) runContext.getVariables().get("execution")).get("id");
        LogRepositoryInterface logRepository = runContext.getApplicationContext().getBean(LogRepositoryInterface.class);
        List<LogEntry> logs = new ArrayList<>();

        if(this.tasksId != null){
            for (String taskId : tasksId) {
                logs.addAll(logRepository.findByExecutionIdAndTaskId(executionId, taskId, level));
            }
        } else {
            logs = logRepository.findByExecutionId(executionId, level);
        }

        File tempFile = runContext.tempFile(".ion").toFile();
        AtomicLong count = new AtomicLong();

        try (OutputStream output = new FileOutputStream(tempFile)) {
            logs.forEach(throwConsumer(log -> {
                count.incrementAndGet();
                FileSerde.write(output, log);
            }));
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
            title = "The size of the rows fetch"
        )
        private Long size;

        @Schema(
            title = "The uri of store result",
            description = "File format is ion"
        )
        private URI uri;
    }
}
