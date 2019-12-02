package org.floworc.core.models.executions;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.floworc.core.models.flows.State;

import javax.validation.constraints.NotNull;
import java.util.List;

@Value
@Builder
public class TaskRunAttempt {
    @With
    private List<LogEntry> logs;

    @With
    private List<MetricEntry> metrics;

    @NotNull
    private State state;

    public TaskRunAttempt withState(State.Type state) {
        return new TaskRunAttempt(
            this.logs,
            this.metrics,
            this.state.withState(state)
        );
    }
}
