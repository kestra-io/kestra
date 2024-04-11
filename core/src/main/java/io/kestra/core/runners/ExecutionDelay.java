package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.flows.State;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

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

    @NotNull DelayType delayType;

    @JsonIgnore
    public String uid() {
        return String.join("_", executionId, taskRunId);
    }

    /**
     * For previous version, return RESUME_FLOW by default as it was the only case
     * @return DelayType representing the action to do when
     */
    public DelayType getDelayType() {
        return delayType == null ? DelayType.RESUME_FLOW : delayType;
    }

    public enum DelayType {
        RESUME_FLOW,
        RESTART_FAILED_TASK,
        RESTART_FAILED_FLOW
    }
}
