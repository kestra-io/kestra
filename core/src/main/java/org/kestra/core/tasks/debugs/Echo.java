package org.kestra.core.tasks.debugs;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;
import org.slf4j.Logger;
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
public class Echo extends Task implements RunnableTask<VoidOutput> {
    @NonNull
    @NotBlank
    private String format;

    @Builder.Default
    private Level level = Level.INFO;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String render = runContext.render(this.format);

        switch (this.level) {
            case TRACE:
                logger.trace(render);
                break;
            case DEBUG:
                logger.debug(render);
                break;
            case INFO:
                logger.info(render);
                break;
            case WARN:
                logger.warn(render);
                break;
            case ERROR:
                logger.error(render);
                break;
            default:
                throw new IllegalArgumentException("Invalid log level '" + this.level + "'");
        }

        return null;
    }
}
