package io.kestra.core.executor.command;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.TaskRun;

/**
 * Sent by the system when the worker holding the folder of a WorkingDirectory is lost while one of its tasks was
 * submitted to it or running on it, so that all the tasks of the WorkingDirectory run again.
 *
 * @param workingDirectoryTaskRunId the task run of the WorkingDirectory to run again
 * @param lostTaskRunId the task run that was lost with the worker; the command is ignored once it is no longer part of
 *     the execution, as the WorkingDirectory already ran again
 */
public record RestartWorkingDirectory(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    String workingDirectoryTaskRunId,
    String lostTaskRunId) implements ExecutionCommand {
    public static RestartWorkingDirectory from(TaskRun lostTaskRun, String workingDirectoryTaskRunId) {
        return new RestartWorkingDirectory(
            lostTaskRun.getTenantId(),
            lostTaskRun.getNamespace(),
            lostTaskRun.getFlowId(),
            lostTaskRun.getExecutionId(),
            Instant.now(),
            EventId.create(),
            workingDirectoryTaskRunId,
            lostTaskRun.getId()
        );
    }
}
