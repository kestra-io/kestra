package io.kestra.core.schedulers;

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
    public Optional<Execution> findById(String id) {
        return executionRepository.findById(id);
    }
}
