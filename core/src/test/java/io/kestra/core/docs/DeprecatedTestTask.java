package io.kestra.core.docs;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(title = "Deprecated documentation test task", description = "A deprecated task fixture.")
@Plugin
public class DeprecatedTestTask extends Task implements RunnableTask<VoidOutput> {
    @Override
    public VoidOutput run(RunContext runContext) {
        return null;
    }
}
