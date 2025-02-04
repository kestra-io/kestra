package io.kestra.plugin.core.debug;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.log.Log;
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

/**
 * @deprecated
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Log a message in the task logs.",
    description = "This task is deprecated, please use the `io.kestra.plugin.core.log.Log` task instead.",
    deprecated = true
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: echo_flow
                namespace: company.team

                tasks:
                  - id: echo
                    type: io.kestra.plugin.core.debug.Echo
                    level: WARN
                    format: "{{ task.id }} > {{ taskrun.startDate }}"
                """
        )
    },
    aliases = "io.kestra.core.tasks.debugs.Echo"
)
@Deprecated
public class Echo extends Task implements RunnableTask<VoidOutput> {
    private Property<String> format;

    @Builder.Default
    private Property<Level> level = Property.of(Level.INFO);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Log log = Log.builder()
            .level(this.level)
            .message(runContext.render(this.format).as(String.class).orElse(null))
            .build();
        log.run(runContext);
        return null;
    }
}
