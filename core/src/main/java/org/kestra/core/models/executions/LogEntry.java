package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import org.slf4j.event.Level;

import java.time.Instant;
import javax.validation.constraints.NotNull;

@Value
@Builder
public class LogEntry {
    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private String taskId;

    @NotNull
    private String executionId;

    @NotNull
    private String taskRunId;

    @NotNull
    private int attemptNumber;

    private Instant timestamp;

    private Level level;

    private String thread;

    private String message;
}
