package io.kestra.core.models.flows.sla;

import java.time.Duration;

/**
 * Marker interface to denote an SLA as evaluating using an {@link SLAMonitor}.
 * ExecutionMonitoringSLA will be evaluated on a deadline defined by the monitor;
 * the monitor is created when the execution is created.
 */
public interface ExecutionMonitoringSLA {
    Duration getDuration();
}
