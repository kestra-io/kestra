package io.kestra.plugin.core.flow;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * First task of a {@link WorkingDirectory}: prepares its folder on the worker its other tasks are then bound to.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin(internal = true)
public class WorkingDirectoryOpen extends Task implements RunnableTask<VoidOutput> {

    @NotNull
    private WorkingDirectory workingDirectory;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        workingDirectory.cleanup(runContext);
        runContext.workingDir().path(true);
        try {
            workingDirectory.preExecuteTasks(runContext);
        } catch (Exception e) {
            workingDirectory.cleanup(runContext);
            throw e;
        }
        return null;
    }
}
