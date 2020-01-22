package org.kestra.core.tasks.debugs;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.metrics.Counter;
import org.kestra.core.models.executions.metrics.Timer;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.slf4j.Logger;

import java.time.Duration;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Return extends Task implements RunnableTask {
    private String format;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        long start = System.nanoTime();

        Logger logger = runContext.logger(this.getClass());

        String render = runContext.render(format);
        logger.debug(render);

        long end = System.nanoTime();

        runContext
            .metric(Counter.of("length", render.length(), "format", format))
            .metric(Timer.of("duration", Duration.ofNanos(end - start), "format", format));

        return RunOutput.builder()
            .outputs(ImmutableMap.of("return", render))
            .build();
    }
}
