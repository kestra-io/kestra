package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.LogEntry;

public interface LogRepositoryInterface {
    ArrayListTotal<LogEntry> findByExecutionId(String id, Pageable pageable);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String TaskId, Pageable pageable);

    ArrayListTotal<LogEntry> find(String query, Pageable pageable);

    LogEntry save(LogEntry log);
}
