package org.kestra.repository.memory;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.LogRepositoryInterface;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
@MemoryRepositoryEnabled
public class MemoryLogRepository implements LogRepositoryInterface {
    private List<LogEntry> logs = new ArrayList<>();

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String id, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String TaskId, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<LogEntry> find(String query, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LogEntry save(LogEntry log) {
        logs.add(log);

        return log;
    }
}
