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

import jakarta.validation.constraints.NotBlank;
import java.util.Collection;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Log a message to the console."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "level: DEBUG",
                "message: \"{{ task.id }} > {{ taskrun.startDate }}\""
            }
        ),
        @Example(
            title = "Log one or more messages to the console.",
            full = true,
            code = """
                id: hello_world
                namespace: dev

                tasks:
                  - id: greeting
                    type: io.kestra.core.tasks.log.Log
                    message:
                      - Kestra team wishes you a great day 👋
                      - If you need some help, reach out via Slack"""
        ),
    }
)
public class Log extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "One or more message(s) to be sent to the backend as logs.",
        description = "It can be a string or an array of strings.",
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
        title = "The log level. If not specified, it defaults to `INFO`."
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


