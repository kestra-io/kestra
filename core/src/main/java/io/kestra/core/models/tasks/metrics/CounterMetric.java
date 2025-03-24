package io.kestra.core.models.tasks.metrics;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CounterMetric extends AbstractMetric {
    public static final String TYPE = "counter";

    @NotNull
    @EqualsAndHashCode.Exclude
    private Property<Double> value;

    @Override
    public AbstractMetricEntry<?> toMetric(RunContext runContext) throws IllegalVariableEvaluationException {
        Optional<String> name = runContext.render(this.name).as(String.class);
        Optional<Double> value = runContext.render(this.value).as(Double.class);
        Map<String, String> tags = runContext.render(this.tags).asMap(String.class, String.class);
        String[] tagsAsStrings = tags.entrySet().stream()
            .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
            .toArray(String[]::new);

        if (name.isEmpty() || value.isEmpty()) {
            throw new IllegalVariableEvaluationException("Metric name and value can't be null");
        }

        return Counter.of(name.get(), value.get(), tagsAsStrings);
    }

    public String getType() {
        return TYPE;
    }
}
