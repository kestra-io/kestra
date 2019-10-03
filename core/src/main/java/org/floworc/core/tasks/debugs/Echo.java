package org.floworc.core.tasks.debugs;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.slf4j.Logger;
import org.slf4j.event.Level;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
public class Echo extends Task implements RunnableTask {
    private String format;

    private Level level = Level.INFO;

    @Override
    public Void run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(Echo.class);

        switch (this.level) {
            case TRACE:
                logger.trace(this.format);
                break;
            case DEBUG:
                logger.debug(this.format);
                break;
            case INFO:
                logger.info(this.format);
                break;
            case WARN:
                logger.warn(this.format);
                break;
            case ERROR:
                logger.error(this.format);
                break;
            default:
                throw new IllegalArgumentException("Invalid log level '" + this.level + "'");
        }

        return null;
    }
}
