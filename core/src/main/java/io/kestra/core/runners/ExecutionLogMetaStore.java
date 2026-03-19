package io.kestra.core.runners;

import io.kestra.core.models.executions.LogEntry;

import java.util.List;

public interface ExecutionLogMetaStore {
    /**
     * Fetches the error logs of an execution.
     * <p>
     * This method limits the results to the first 25 error logs, ordered by timestamp asc.
     *
     * @return the log entries
     */
    List<LogEntry> errorLogs(String tenantId, String executionId);
}
