package io.kestra.core.models.tasks.metrics;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TimerMetric extends AbstractMetric {
    public static final String TYPE = "timer";

    @NotNull
    @EqualsAndHashCode.Exclude
    private Property<Duration> value;

    @Override
    public AbstractMetricEntry<?> toMetric(RunContext runContext) throws IllegalVariableEvaluationException {
        Optional<String> name = runContext.render(this.name).as(String.class);
        Optional<Duration> value = runContext.render(this.value).as(Duration.class);
        Map<String, String> tags = runContext.render(this.tags).asMap(String.class, String.class);
        String[] tagsAsStrings = tags.entrySet().stream()
            .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
            .toArray(String[]::new);

        if (name.isEmpty() || value.isEmpty()) {
            throw new IllegalVariableEvaluationException("Metric name and value can't be null");
        }

        return Timer.of(name.get(), value.get(), tagsAsStrings);
    }

    public String getType() {
        return TYPE;
    }
}
