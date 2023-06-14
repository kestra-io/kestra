package io.kestra.core.tasks.log;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.annotation.NonNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import javax.validation.constraints.NotBlank;
import java.util.Collection;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Log a message in the task logs"
)
@Plugin(
    examples = {
        @Example(
            code = {
                "level: WARN",
                "message: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        ),
        @Example(
            code = {
                "level: WARN",
                "message: " +
                "   - 'Task id : \"{{task.id}}\"'" +
                "   - 'Start date: \"{{taskrun.startDate}}\"'"
            }
        )
    }
)
public class Log extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "The message(s) to log",
        description = "Can be a string or an array of string",
        anyOf = {
            String.class,
            String[].class
        }
    )
    @NonNull
    @NotBlank
    @PluginProperty(dynamic = true)
    private Object message;

    @Schema(
        title = "The log level"
    )
    @Builder.Default
    @PluginProperty
    private Level level = Level.INFO;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        if(this.message instanceof String) {
            String render = runContext.render((String) this.message);
            this.log(logger, this.level, render);
        } else if (this.message instanceof Collection) {
            Collection<String> messages = (Collection<String>) this.message;
            messages.forEach(throwConsumer(message -> {
                String render;
                render = runContext.render(message);
                this.log(logger, this.level, render);
            }));
        } else {
            throw new IllegalArgumentException("Invalid message type '" + this.message.getClass() + "'");
        }

        return null;
    }

    public void log(Logger logger, Level level, String message) {
        switch (this.level) {
            case TRACE:
                logger.trace(message);
                break;
            case DEBUG:
                logger.debug(message);
                break;
            case INFO:
                logger.info(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case ERROR:
                logger.error(message);
                break;
            default:
                throw new IllegalArgumentException("Invalid log level '" + this.level + "'");
        }
    }
}


