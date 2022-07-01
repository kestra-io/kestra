package io.kestra.repository.memory;

import io.micronaut.data.model.Pageable;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.inject.Singleton;

import javax.annotation.Nullable;

@Singleton
@MemoryRepositoryEnabled
public class MemoryLogRepository implements LogRepositoryInterface {
    private final List<LogEntry> logs = new ArrayList<>();

    @Override
    public List<LogEntry> findByExecutionId(String id, Level minLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel) {
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
}
