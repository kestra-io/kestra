package io.kestra.runner.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.schedulers.SchedulerExecutionStateInterface;

import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MemoryQueueEnabled
public class MemorySchedulerExecutionState implements SchedulerExecutionStateInterface {
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Override
    public Optional<Execution> findById(String id) {
        return executionRepository.findById(id);
    }
}
