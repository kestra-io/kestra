package io.kestra.core.runners;

import java.time.Instant;

import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowLogEvent(
    String namespace,
    String flowId,
    String taskId,
    String executionId,
    String taskRunId,
    Integer attemptNumber,
    String triggerId,
    Instant timestamp,
    Level level,
    String message,
    ExecutionKind executionKind,
    String progress) implements BroadcastEvent {
    public static FollowLogEvent from(LogEntry logEntry) {
        return new FollowLogEvent(
            logEntry.getNamespace(), logEntry.getFlowId(), logEntry.getTaskId(), logEntry.getExecutionId(), logEntry.getTaskRunId(), logEntry.getAttemptNumber(),
            logEntry.getTriggerId(), logEntry.getTimestamp(), logEntry.getLevel(), logEntry.getMessage(), logEntry.getExecutionKind(), logEntry.getProgress()
        );
    }

    @Override
    public String key() {
        return IdUtils.create();
    }
}
