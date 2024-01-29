package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.flows.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class ExecutionDelay {
    @NotNull
    String taskRunId;

    @NotNull
    String executionId;

    @NotNull
    Instant date;

    @NotNull State.Type state;

    @JsonIgnore
    public String uid() {
        return String.join("_", executionId, taskRunId);
    }
}
