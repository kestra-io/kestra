package io.kestra.core.runners;

import org.slf4j.event.Level;

import io.kestra.core.repositories.LogRepositoryInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DefaultExecutionLogMetaStore implements ExecutionLogMetaStore {
    private final LogRepositoryInterface logRepository;

    @Inject
    public DefaultExecutionLogMetaStore(LogRepositoryInterface logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public java.util.List<io.kestra.core.models.executions.LogEntry> errorLogs(String tenantId, String executionId) {
        return logRepository.findByExecutionId(tenantId, executionId, Level.ERROR, Pageable.from(1, 25, Sort.of(Sort.Order.asc("timestamp"))));
    }
}
