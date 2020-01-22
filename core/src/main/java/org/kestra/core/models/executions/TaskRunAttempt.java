package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.models.flows.State;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@Value
@Builder
public class TaskRunAttempt {
    @With
    private List<LogEntry> logs;

    @With
    private List<AbstractMetricEntry<?>> metrics;

    @NotNull
    private State state;

    public TaskRunAttempt withState(State.Type state) {
        return new TaskRunAttempt(
            this.logs,
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
