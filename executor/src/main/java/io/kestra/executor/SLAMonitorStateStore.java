package io.kestra.executor;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.runners.RunContext;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * This state store is used by the {@link io.kestra.core.runners.Executor} to handle flow SLA.
 */
public interface SLAMonitorStateStore {
    /**
     * Save an SLA monitor.
     */
    void save(SLAMonitor slaMonitor);

    /**
     * Purge all SLA monitors for a given execution.
     */
    void purge(String executionId);

    /**
     * Process expired SLA monitors using the provided consumer.
     * This method is used periodically by the {@link io.kestra.core.runners.Executor} to check if some SLA monitors are expired.
     * Expired SLA monitors will be sent to the consumer for processing via the {@link SLAService#evaluateExecutionMonitoringSLA(RunContext, Execution, SLA)}.
     *
     * @implNote Implementors must use some sort of transaction (FOR UPDATE SKIP LOCKED or {@link io.kestra.core.lock.LockService#tryLock(String, String, Runnable)}) for accuracy.
     */
    void processExpired(Instant now, Consumer<SLAMonitor> consumer);
}
