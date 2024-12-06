package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.sla.ExecutionChangedSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class SLAService {

    /**
     * Evaluate execution changed SLA of a flow for an execution.
     * Each violated SLA will be logged.
     */
    public List<Violation> evaluateExecutionChangedSLA(RunContext runContext, Flow flow, Execution execution) {
        return ListUtils.emptyOnNull(flow.getSla()).stream()
            .filter(ExecutionChangedSLA.class::isInstance)
            .map(
                sla -> {
                    try {
                        return sla.evaluate(runContext, execution);
                    } catch (Exception e) {
                        runContext.logger().error("Ignoring SLA '{}' because of the error: {}", sla.getId(), e.getMessage(), e);
                        return Optional.<Violation>empty();
                    }
                }
            )
            .flatMap(violation -> violation.stream())
            .peek(violation -> runContext.logger().warn("SLA '{}' violated: {}", violation.slaId(), violation.reason()))
            .toList();
    }

    /**
     * Evaluate a single SLA for an execution.
     * Each violated SLA will be logged.
     */
    public Optional<Violation> evaluateExecutionMonitoringSLA(RunContext runContext, Execution execution, SLA sla) {
        try {
            Optional<Violation> maybeViolation = sla.evaluate(runContext, execution);
            maybeViolation.ifPresent(violation -> runContext.logger().warn("SLA '{}' violated: {}", violation.slaId(), violation.reason()));
            return maybeViolation;
        } catch (Exception e) {
            runContext.logger().error("Ignoring SLA '{}' because of the error: {}", sla.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}
