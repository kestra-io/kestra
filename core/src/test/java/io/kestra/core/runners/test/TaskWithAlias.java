package io.kestra.core.runners.test;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor

@Plugin(
    aliases = "io.kestra.core.runners.test.task.Alias"
)
public class TaskWithAlias extends Task implements RunnableTask<VoidOutput> {
    @NotNull
    @PluginProperty(dynamic = true)
    private String message;


    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        logger.info(message);

        return null;
    }
}


