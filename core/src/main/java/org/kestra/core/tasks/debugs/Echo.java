package org.kestra.core.tasks.debugs;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;
import org.slf4j.Logger;
import org.slf4j.event.Level;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Simple debugging task that log a renderer value.",
    body = {
        "This task is mostly useful for debugging purpose.",
        "",
        "This one allow you to logs inputs or outputs variables for example, or to debug some templated functions."
    }
)
@Example(
    code = {
        "level: WARN",
        "format: \"{{task.id}} > {{taskrun.startDate}}\""
    }
)
public class Echo extends Task implements RunnableTask<VoidOutput> {
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
