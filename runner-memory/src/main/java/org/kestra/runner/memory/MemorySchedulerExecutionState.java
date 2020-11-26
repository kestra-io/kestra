package org.kestra.runner.memory;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.schedulers.SchedulerExecutionStateInterface;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

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
