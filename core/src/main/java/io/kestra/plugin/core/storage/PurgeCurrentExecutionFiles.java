package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Purge all files from Kestra's internal storage created by this execution.",
    description = "This will delete all the generated files from a flow for the current execution. This will delete all files from:\n" +
        "- inputs\n" +
        "- outputs\n" +
        "- triggers\n\n" +
        "If the current execution doesn't have any generated files, the task will not fail."
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all files for the current execution",
            code = {
            }
        ),
        @Example(
            title = "Purge files including child executions and state files",
            code = {
                "includeChildExecutions: true",
                "includeStates: true"
            }
        )
    },
    aliases = {"io.kestra.core.tasks.storages.PurgeExecution", "io.kestra.plugin.core.storage.PurgeExecution"}
)
public class PurgeCurrentExecutionFiles extends Task implements RunnableTask<PurgeCurrentExecutionFiles.Output> {
    @Schema(
        title = "Whether to purge files from child executions (subflows)",
        description = "When set to true, this will also purge files from all child executions triggered by subflow tasks."
    )
    @Builder.Default
    private Property<Boolean> includeChildExecutions = Property.ofValue(false);

    @Schema(
        title = "Whether to purge state files created by Set tasks",
        description = "When set to true, this will also purge state files created by io.kestra.plugin.core.state.Set tasks."
    )
    @Builder.Default
    private Property<Boolean> includeStates = Property.ofValue(false);

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
