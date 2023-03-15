package io.kestra.core.tasks.debugs;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.tasks.log.Log;
import io.micronaut.core.annotation.NonNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import org.slf4j.event.Level;

import javax.validation.constraints.NotBlank;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Simple debugging task that log a renderer value.",
    description = "This task is mostly useful for debugging purpose.\n\n" +
        "This one allow you to logs inputs or outputs variables for example, or to debug some templated functions."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "level: WARN",
                "format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
@Deprecated
public class Echo extends Task implements RunnableTask<VoidOutput> {
    @NonNull
    @NotBlank
    @PluginProperty(dynamic = true)
    private String format;

    @Builder.Default
    @PluginProperty
    private Level level = Level.INFO;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Log log = Log.builder()
            .level(this.level)
            .message(this.format)
            .build();
        log.run(runContext);
        return null;
    }
}
