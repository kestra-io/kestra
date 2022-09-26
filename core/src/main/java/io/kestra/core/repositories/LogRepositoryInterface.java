package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.micronaut.data.model.Pageable;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;
import javax.annotation.Nullable;

public interface LogRepositoryInterface extends SaveRepositoryInterface<LogEntry> {
    List<LogEntry> findByExecutionId(String id, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel);

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
