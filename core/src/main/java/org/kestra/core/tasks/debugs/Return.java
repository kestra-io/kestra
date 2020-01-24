package org.kestra.core.tasks.debugs;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.metrics.Counter;
import org.kestra.core.models.executions.metrics.Timer;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.slf4j.Logger;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Return extends Task implements RunnableTask<Return.Output> {
    private String format;

    @Override
    public Return.Output run(RunContext runContext) throws Exception {
        long start = System.nanoTime();

        Logger logger = runContext.logger(this.getClass());

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
    public static class Output implements org.kestra.core.models.tasks.Output {
        private String value;
    }
}
