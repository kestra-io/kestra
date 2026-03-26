package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.models.executions.LogEntry;

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
