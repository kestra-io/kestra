package io.kestra.plugin.core.metric;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.metrics.AbstractMetric;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Publish metrics.",
    description = "This task is useful to easily publish metrics for a flow."
)
public class Publish extends Task implements RunnableTask<Publish.Output> {

    @Schema(
        title = "List of metrics to publish."
    )
    private Property<List<AbstractMetric>> metrics;

    @Override
    public Output run(RunContext runContext) throws Exception {

        runContext.render(metrics).asList(AbstractMetric.class)
            .stream()
            .map(abstractMetric -> {
                try {
                    return abstractMetric.toMetric(runContext);
                } catch (IllegalVariableEvaluationException e) {
                    throw new RuntimeException(e);
                }
            }).toList().forEach(runContext::metric);;

        return Output.builder()
            .metrics(runContext.render(metrics).asList(AbstractMetric.class)
                .stream()
                .map(abstractMetric -> {
                    try {
                        return abstractMetric.toMetric(runContext);
                    } catch (IllegalVariableEvaluationException e) {
                        throw new RuntimeException(e);
                    }
                }).toList())
            .build();
    }



@Builder
@Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final List<? extends AbstractMetricEntry<?>> metrics;
    }
}


