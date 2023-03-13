package io.kestra.core.tasks.debugs;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import org.slf4j.Logger;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Debugging task that returns a rendered value.",
    description = "This task is mostly useful for debugging purpose.\n\n" +
        "It allows you to see inputs or outputs variables or to debug some templated functions."
)
@Plugin(
    examples = {
        @Example(
            code = "format: \"{{task.id}} > {{taskrun.startDate}}\""
        )
    }
)
public class Return extends Task implements RunnableTask<Return.Output> {
    @Schema(
        title = "The templated string to render"
    )
    @PluginProperty(dynamic = true)
    private String format;

    @Override
    public Return.Output run(RunContext runContext) throws Exception {
        long start = System.nanoTime();

        Logger logger = runContext.logger();

        String render = runContext.render(format);
        logger.debug(render);

        long end = System.nanoTime();

        runContext
            .metric(Counter.of("length", render.length(), "format", format))
            .metric(Timer.of("duration", Duration.ofNanos(end - start), "format", format));

        return Output.builder()
            .value(render)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated string"
        )
        private String value;
    }
}
