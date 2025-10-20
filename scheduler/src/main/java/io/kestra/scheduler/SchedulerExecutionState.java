package io.kestra.scheduler;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;

import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SchedulerExecutionState implements SchedulerExecutionStateInterface {
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Override
    public Optional<Execution> findById(String tenantId, String id) {
        return executionRepository.findById(tenantId, id);
    }
}
