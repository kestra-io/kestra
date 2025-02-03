package io.kestra.plugin.core.metric;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
    title = "Publish metrics.",
    description = "This task is useful to easily publish metrics for a flow."
)
public class Publish extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "List of metrics to publish."
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



