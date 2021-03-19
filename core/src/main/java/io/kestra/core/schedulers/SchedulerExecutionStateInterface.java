package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;

import java.util.Optional;

public interface SchedulerExecutionStateInterface {
    Optional<Execution> findById(String id);
}
