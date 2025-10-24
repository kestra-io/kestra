package io.kestra.core.models.tasks.metrics;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.stream.Stream;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GaugeMetric extends AbstractMetric {
    public static final String TYPE = "gauge";

    @NotNull
    @EqualsAndHashCode.Exclude
    private Property<Double> value;

    @Override
    public AbstractMetricEntry<?> toMetric(RunContext runContext) throws IllegalVariableEvaluationException {
        String name = runContext.render(this.name).as(String.class).orElseThrow();
        Double value = runContext.render(this.value).as(Double.class).orElseThrow();
        String description = runContext.render(this.description).as(String.class).orElse(null);
        Map<String, String> tags = runContext.render(this.tags).asMap(String.class, String.class);
        String[] tagsAsStrings = tags.entrySet().stream()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);

        return Gauge.of(name, description, value, tagsAsStrings);
    }

    public String getType() {
        return TYPE;
    }
}
