package io.kestra.core.models.flows.sla;

import io.kestra.core.runners.RunContext;
import java.time.Duration;
import java.util.Optional;

/**
 * Marker interface to denote an SLA as evaluating using an {@link SLAMonitor}.
 * ExecutionMonitoringSLA will be evaluated on a deadline defined by the monitor;
 * the monitor is created when the execution is created.
 */
public interface ExecutionMonitoringSLA {
    Optional<Duration> computeDuration(RunContext runContext);
}
