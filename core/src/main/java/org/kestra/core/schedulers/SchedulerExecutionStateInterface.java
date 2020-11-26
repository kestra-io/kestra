package org.kestra.core.schedulers;

import org.kestra.core.models.executions.Execution;

import java.util.Optional;

public interface SchedulerExecutionStateInterface {
    Optional<Execution> findById(String id);
}
