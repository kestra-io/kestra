package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.LogEntry;
import org.slf4j.event.Level;

import java.util.List;

public interface LogRepositoryInterface {
    List<LogEntry> findByExecutionId(String id, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String TaskId, Level minLevel);

    ArrayListTotal<LogEntry> find(String query, Pageable pageable);

    LogEntry save(LogEntry log);
}
