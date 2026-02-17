package io.kestra.plugin.core.metric;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.tasks.metrics.AbstractMetric;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Publish custom metrics from a Flow execution.",
    description = """
        Renders and emits the provided list of metrics (counters, timers, etc.) during the Flow. Tags can include Flow or Namespace metadata for later filtering.

        Use when downstream monitoring/alerting depends on execution-time signals beyond built-in metrics."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: publish_metrics
                namespace: company.team

                tasks:
                  - id: metric
                    type: io.kestra.plugin.core.metric.Publish
                    metrics:
                      - type: timer
                        name: duration
                        value: PT10M
                        tags:
                          flow: "{{flow.id}}"
                          project: kestra
                      - type: counter
                        name: number
                        value: 42
                        tags:
                          flow: "{{flow.id}}"
                          project: kestra
                """
        )
    }
)
public class Publish extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "List of metrics to publish"
    )
    private Property<List<AbstractMetric>> metrics;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {

        runContext.render(metrics).asList(AbstractMetric.class)
            .stream()
            .map(abstractMetric -> {
                try {
                    return abstractMetric.toMetric(runContext);
                } catch (IllegalVariableEvaluationException e) {
                    throw new RuntimeException(e);
                }
            }).toList().forEach(runContext::metric);

        return null;
    }
}


