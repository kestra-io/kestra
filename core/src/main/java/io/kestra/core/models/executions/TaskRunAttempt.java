package io.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import io.kestra.core.models.flows.State;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;

@Value
@Builder
public class TaskRunAttempt {
    @With
    List<AbstractMetricEntry<?>> metrics;

    @NotNull
    State state;

    public TaskRunAttempt withState(State.Type state) {
        return new TaskRunAttempt(
            this.metrics,
            this.state.withState(state)
        );
    }

    public Optional<AbstractMetricEntry<?>> findMetrics(String name) {
        return this.metrics
            .stream()
            .filter(metricEntry -> metricEntry.getName().equals(name))
            .findFirst();
    }
}
