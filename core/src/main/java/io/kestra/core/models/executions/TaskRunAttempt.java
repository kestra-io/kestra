package io.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.flows.State;

import java.net.URI;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.With;

@Value
@Builder
public class TaskRunAttempt {
    /**
     * @deprecated Should always be null, we need to keep it for backward compatibility or the deserialization of old attempt will no longer work.
     */
    @Deprecated
    public void setMetrics(List<AbstractMetricEntry<?>> metrics) {

    }

    @NotNull
    State state;

    @With
    URI logFile;

    public TaskRunAttempt withState(State.Type state) {
        return new TaskRunAttempt(
            this.state.withState(state),
            this.logFile
        );
    }
}