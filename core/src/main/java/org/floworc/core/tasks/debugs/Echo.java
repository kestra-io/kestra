package org.floworc.core.tasks.debugs;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.slf4j.Logger;
import org.slf4j.event.Level;

@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@FieldDefaults(level= AccessLevel.PROTECTED)
@AllArgsConstructor
public class Echo extends Task implements RunnableTask {
    private String format;

    @Builder.Default
    private Level level = Level.INFO;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(Echo.class);

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
