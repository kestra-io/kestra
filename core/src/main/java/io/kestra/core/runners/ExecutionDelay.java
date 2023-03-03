package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import javax.validation.constraints.NotNull;

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
}
