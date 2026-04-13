package io.kestra.core.models.executions;

import java.net.URI;

import io.kestra.core.models.flows.State;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class TaskRunAttempt {
    @NotNull
    State state;

    @Nullable
    String workerId;

    @With
    @Nullable
    URI logFile;

    public TaskRunAttempt withState(State.Type state) {
        return new TaskRunAttempt(
            this.state.withState(state),
            this.workerId,
            this.logFile
        );
    }
}