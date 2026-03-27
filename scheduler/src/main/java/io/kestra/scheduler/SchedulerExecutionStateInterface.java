package io.kestra.scheduler;

import java.util.Optional;

import io.kestra.core.models.executions.Execution;

public interface SchedulerExecutionStateInterface {
    Optional<Execution> findById(String tenantId, String id);
}
