package io.kestra.plugin.core.log;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.event.Level;

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
                namespace: company.team

                tasks:
                  - id: greeting
                    type: io.kestra.plugin.core.log.Log
                    message:
                      - Kestra team wishes you a great day 👋
                      - If you need some help, reach out via Slack"""
        ),
    },
    aliases = "io.kestra.core.tasks.log.Log"
)
public class Log extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "One or more message(s) to be sent to the backend as logs.",
        description = "It can be a string or an array of strings.",
        oneOf = {
            String.class,
            String[].class
        }
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Object message;

    @Schema(
        title = "The log level. If not specified, it defaults to `INFO`."
    )
    @Builder.Default
    private Property<Level> level = Property.of(Level.INFO);

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        var renderedLevel = runContext.render(this.level).as(Level.class).orElseThrow();

        if(this.message instanceof String stringValue) {
            String render = runContext.render(stringValue);
            this.log(logger, renderedLevel, render);
        } else if (this.message instanceof Collection<?> collectionValue) {
            Collection<String> messages = (Collection<String>) collectionValue;
            messages.forEach(throwConsumer(message -> {
                String render;
                render = runContext.render(message);
                this.log(logger, renderedLevel, render);
            }));
        } else {
            throw new IllegalArgumentException("Invalid message type '" + this.message.getClass() + "'");
        }

        return null;
    }

    public void log(Logger logger, Level level, String message) {
        switch (level) {
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
                throw new IllegalArgumentException("Invalid log level '" + this.level.toString() + "'");
        }
    }
}


