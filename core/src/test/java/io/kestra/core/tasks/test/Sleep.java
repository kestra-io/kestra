package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

/**
 * This task is used in unit tests where we need a task that wait a little to be able to check the status of running tasks.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Sleep extends Task implements RunnableTask<VoidOutput> {

    @PluginProperty
    @NotNull
    private Long duration;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Thread.sleep(duration);
        return null;
    }
}
