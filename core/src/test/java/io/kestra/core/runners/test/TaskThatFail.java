package io.kestra.core.runners.test;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
public class TaskThatFail extends Task implements RunnableTask<TaskThatFail.Output> {
    @NotNull
    @PluginProperty(dynamic = true)
    private String message;


    @Override
    public TaskThatFail.Output run(RunContext runContext) throws Exception {
        var output = Output.builder().message(this.message).build();
        throw new RunnableTaskException("An exception occurs", output);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String message;
    }
}
