package io.kestra.core.plugins.test;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import javax.validation.constraints.NotBlank;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Deprecated
public class DeprecatedTask extends Task implements RunnableTask<VoidOutput> {
    @NotBlank
    @PluginProperty(dynamic = true)
    @Deprecated
    private String message;

    @PluginProperty(dynamic = true)
    @Deprecated
    private String someProperty;

    @Builder.Default
    @PluginProperty
    private Level level = Level.INFO;

    @Override
    public VoidOutput run(RunContext runContext) {
        return null;
    }
}
