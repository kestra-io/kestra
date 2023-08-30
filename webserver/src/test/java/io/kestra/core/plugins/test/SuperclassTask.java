package io.kestra.core.plugins.test;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Deprecated
public class SuperclassTask extends Task implements RunnableTask<VoidOutput> {
    @PluginProperty(dynamic = true)
    @Deprecated
    private String someProperty;

    @Override
    public VoidOutput run(RunContext runContext) {
        return null;
    }
}
