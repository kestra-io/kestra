package io.kestra.plugin.core.debug;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Return a value for debugging purposes.",
    description = """
        Render a templated string and return it so you can quickly inspect or reuse values during a flow.

        The rendered value is logged. Use only non-sensitive data when troubleshooting. For production workflows, use `io.kestra.plugin.core.log.Log` to display a message or `io.kestra.plugin.core.output.OutputValues` to pass values downstream. Inject secrets directly into the task properties that need them."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: debug_value
                namespace: company.team

                tasks:
                  - id: return
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ task.id }} > {{ taskrun.startDate }}"
                """
        ),
        @Example(
            full = true,
            code = """
                id: debug_greeting
                namespace: company.team

                inputs:
                  - id: name
                    type: STRING
                    required: false

                tasks:
                  - id: greeting
                    type: io.kestra.plugin.core.debug.Return
                    format: >-
                      {%- if inputs.name is not empty -%}
                      Hello {{ inputs.name }}
                      {%- else -%}
                      Hello world
                      {%- endif -%}
                """
        )
    },
    metrics = {
        @Metric(name = "length", type = Counter.TYPE),
        @Metric(name = "duration", type = Timer.TYPE)
    }
)
public class Return extends Task implements RunnableTask<Return.Output> {
    @Schema(
        title = "The templated string to render."
    )
    private Property<String> format;

    @Override
    public Return.Output run(RunContext runContext) throws Exception {
        long start = System.nanoTime();

        Logger logger = runContext.logger();

        String render = runContext.render(format).as(String.class).orElse(null);
        logger.debug(render);

        long end = System.nanoTime();

        runContext
            .metric(Counter.of("length", Optional.ofNullable(render).map(String::length).orElse(0)))
            .metric(Timer.of("duration", Duration.ofNanos(end - start)));

        return Output.builder()
            .value(render)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated string."
        )
        private String value;
    }
}
