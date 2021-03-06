package io.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import io.kestra.core.models.executions.LogEntry;
import org.slf4j.event.Level;

import java.util.List;

public interface LogRepositoryInterface extends SaveRepositoryInterface<LogEntry> {
    List<LogEntry> findByExecutionId(String id, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel);

    ArrayListTotal<LogEntry> find(String query, Pageable pageable, Level minLevel);

    LogEntry save(LogEntry log);
}
