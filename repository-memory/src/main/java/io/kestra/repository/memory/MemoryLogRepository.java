package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryLogRepository implements LogRepositoryInterface {
    private final List<LogEntry> logs = new ArrayList<>();

    @Override
    public List<LogEntry> findByExecutionId(String id, Level minLevel) {
        return logs
            .stream()
            .filter(logEntry -> logEntry.getExecutionId().equals(id) && logEntry.getLevel().equals(minLevel))
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String id, Level minLevel, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel) {
        return logs
            .stream()
            .filter(logEntry -> logEntry.getExecutionId().equals(executionId) && logEntry.getTaskId().equals(taskId) && logEntry.getLevel().equals(minLevel))
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String executionId, String taskRunId, Level minLevel, Integer attempt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LogEntry save(LogEntry log) {
        logs.add(log);

        return log;
    }

    @Override
    public Integer purge(Execution execution) {
        throw new UnsupportedOperationException();
    }
}
