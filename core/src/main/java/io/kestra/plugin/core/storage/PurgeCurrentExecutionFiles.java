package io.kestra.plugin.core.storage;

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
    title = "Purge files created by the current Execution.",
    description = """
        Deletes all internal-storage files produced by this Execution (inputs, outputs, triggers). No-op if nothing was generated."""
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all files created by this execution.",
            full = true,
            code = """
                id: purge_execution_files
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/json/user_events.json

                  - id: purge
                    type: io.kestra.plugin.core.storage.PurgeCurrentExecutionFiles
            """
        )
    },
    aliases = {"io.kestra.core.tasks.storages.PurgeExecution", "io.kestra.plugin.core.storage.PurgeExecution"}
)
public class PurgeCurrentExecutionFiles extends Task implements RunnableTask<PurgeCurrentExecutionFiles.Output> {
    @Override
    public PurgeCurrentExecutionFiles.Output run(RunContext runContext) throws Exception {
        return Output.builder()
            .uris(runContext.storage().deleteExecutionFiles())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The deleted file URIs from Kestra's internal storage"
        )
        private final List<URI> uris;
    }
}
