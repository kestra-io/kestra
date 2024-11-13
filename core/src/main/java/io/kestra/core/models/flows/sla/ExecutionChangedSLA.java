package io.kestra.core.models.flows.sla;

/**
 * Marker interface to denote an SLA as evaluating on execution change.
 * ExecutionChangedSLA will be evaluated on each execution change, a.k.a. at the beginning of the processing of the execution queue.
 */
public interface ExecutionChangedSLA {
}
