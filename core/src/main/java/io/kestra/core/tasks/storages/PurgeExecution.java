package io.kestra.core.tasks.storages;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge all files from the internal storage created by this execution.",
    description = "Will delete all the generated files from a flow for the current execution. This will delete all files from:\n" +
        "- inputs\n" +
        "- outputs\n" +
        "- triggers\n\n" +
        "If the current execution doesn't have any generated files, the task will not fail."
)
@Plugin(
    examples = {
        @Example(
            code = {
            }
        )
    }
)
public class PurgeExecution extends Task implements RunnableTask<PurgeExecution.Output> {
    @Override
    public PurgeExecution.Output run(RunContext runContext) throws Exception {
        return Output.builder()
            .uris(runContext.purgeStorageExecution())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The deleted files"
        )
        private final List<URI> uris;
    }
}
