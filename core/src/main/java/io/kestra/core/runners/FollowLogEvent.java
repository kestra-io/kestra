package io.kestra.core.runners;

import java.time.Instant;

import org.slf4j.event.Level;

import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

// TODO we may want to restrict to what's used in the UI
public record FollowLogEvent(
    String tenantId,
    String namespace,
    String flowId,
    String taskId,
    String executionId,
    String taskRunId,
    Integer attemptNumber,
    String triggerId,
    Instant timestamp,
    Level level,
    String thread,
    String message,
    ExecutionKind executionKind) implements BroadcastEvent {
    public static FollowLogEvent from(LogEntry logEntry) {
        return new FollowLogEvent(
            logEntry.getTenantId(), logEntry.getNamespace(), logEntry.getFlowId(), logEntry.getTaskId(), logEntry.getExecutionId(), logEntry.getTaskRunId(), logEntry.getAttemptNumber(),
            logEntry.getTriggerId(), logEntry.getTimestamp(), logEntry.getLevel(), logEntry.getThread(), logEntry.getMessage(), logEntry.getExecutionKind()
        );
    }

    @Override
    public String key() {
        // FIXME should we return null instead?
        return IdUtils.create();
    }
}
