package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.micronaut.data.model.Pageable;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.List;

public interface LogRepositoryInterface extends SaveRepositoryInterface<LogEntry> {
    List<LogEntry> findByExecutionId(String id, Level minLevel);

    ArrayListTotal<LogEntry> findByExecutionId(String id, Level minLevel, Pageable pageable);

    List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel, Pageable pageable);

    List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel, Pageable pageable);

    List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String executionId, String taskRunId, Level minLevel, Integer attempt);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable);

    ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    );


    LogEntry save(LogEntry log);

    Integer purge(Execution execution);
}
