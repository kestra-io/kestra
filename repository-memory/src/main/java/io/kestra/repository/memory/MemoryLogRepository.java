package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;

import jakarta.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryLogRepository implements LogRepositoryInterface {
    private final List<LogEntry> logs = new ArrayList<>();

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel) {
        return logs
            .stream()
            .filter(logEntry -> logEntry.getExecutionId().equals(executionId) && logEntry.getLevel().equals(minLevel))
            .filter(logEntry -> (tenantId == null && logEntry.getTenantId() == null) || (tenantId != null && tenantId.equals(logEntry.getTenantId())))
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String tenantId, String id, Level minLevel, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String namespace, String flowId, String executionId, Level level) {
        return findByExecutionId(tenantId, executionId, level);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel) {
        return logs
            .stream()
            .filter(logEntry -> logEntry.getExecutionId() != null && logEntry.getExecutionId().equals(executionId) && logEntry.getTaskId().equals(taskId) && logEntry.getLevel().equals(minLevel))
            .filter(logEntry -> (tenantId == null && logEntry.getTenantId() == null) || (tenantId != null && tenantId.equals(logEntry.getTenantId())))
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String namespace, String flowId, String executionId, String taskId, Level level) {
        return findByExecutionIdAndTaskId(tenantId, executionId, taskId, level);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogStatistics> statistics(@io.micronaut.core.annotation.Nullable String query, @io.micronaut.core.annotation.Nullable String tenantId, @io.micronaut.core.annotation.Nullable String namespace, @io.micronaut.core.annotation.Nullable String flowId, @io.micronaut.core.annotation.Nullable Level minLevel, @io.micronaut.core.annotation.Nullable ZonedDateTime startDate, @io.micronaut.core.annotation.Nullable ZonedDateTime endDate, @io.micronaut.core.annotation.Nullable DateUtils.GroupType groupBy) {
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

    @Override
    public void deleteByQuery(String tenantId, String executionId, String taskId, String taskRunId, Level minLevel, Integer attempt) {
        throw new UnsupportedOperationException();
    }
}
