package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import org.slf4j.event.Level;

import java.util.List;

public class DefaultTaskRunLogs implements TaskRunLogs {
    private final LogDataStoreInterface logStore;
    private final String tenantId;
    private final String executionId;
    private final String taskRunId;

    public DefaultTaskRunLogs(LogDataStoreInterface logStore, String tenantId, String executionId, String taskRunId) {
        this.logStore = logStore;
        this.tenantId = tenantId;
        this.executionId = executionId;
        this.taskRunId = taskRunId;
    }

    @Override
    public List<LogEntry> find(Level minLevel) {
        return logStore.findByExecutionIdAndTaskRunId(tenantId, executionId, taskRunId, minLevel);
    }
}
