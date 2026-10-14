package io.kestra.plugin.core.flow;

import java.nio.file.Files;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Last task of a {@link WorkingDirectory}, run even when one of its tasks failed: saves the output files and the
 * cache, then deletes the folder.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin(internal = true)
public class WorkingDirectoryClose extends Task implements RunnableTask<VoidOutput> {

    @NotNull
    private WorkingDirectory workingDirectory;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        // a WorkingDirectoryOpen that failed already deleted the folder
        if (!Files.exists(runContext.workingDir().path(false))) {
            return null;
        }
        try {
            workingDirectory.postExecuteTasks(runContext);
        } finally {
            workingDirectory.cleanup(runContext);
        }
        return null;
    }
}
