package org.kestra.repository.memory;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.LogRepositoryInterface;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

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
    public ArrayListTotal<LogEntry> find(String query, Pageable pageable, Level minLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LogEntry save(LogEntry log) {
        logs.add(log);

        return log;
    }
}
