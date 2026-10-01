package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.ListUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes expired {@link SLAMonitor}s — execution-monitoring SLAs whose deadline has passed —
 * and returns the resulting {@link ExecutorContext}s to the caller.
 * <p>
 * IMPORTANT — transactional outbox: {@link #processExpired(Instant)} runs its callback inside the
 * SLA-monitor state store's transaction. Queue messages must never be emitted from inside that
 * transaction: on brokers with their own transactionality (e.g. Kafka), a consumer can observe
 * the message before the state-store transaction commits and act on state that does not exist
 * yet. This class therefore emits nothing itself — it only collects and returns the contexts,
 * and the caller emits them <b>after</b> this method returns, i.e. after the transaction has
 * committed. Same rule as {@link ExecutionDelayProcessor}.
 * <p>
 * Known residual: for FAIL/CANCEL behaviors, {@link ExecutorService#processViolation} internally
 * emits an {@code ExecutionKilledExecution} on the kill queue, still inside the execution lock.
 * That emission is shared with the execution-changed SLA path
 * ({@link ExecutorService#handleExecutionChangedSLA}) and needs its own outbox seam (an emission
 * accumulator on {@link ExecutorContext}) — out of scope here.
 */
@Singleton
@Slf4j
public class SLAMonitorProcessor {
    private final SLAMonitorStateStore slaMonitorStateStore;
    private final ExecutionStateStore executionStateStore;
    private final FlowMetaStoreInterface flowMetaStore;
    private final ExecutionService executionService;
    private final ExecutorService executorService;
    private final SLAService slaService;
    private final RunContextFactory runContextFactory;
    private final MetricRegistry metricRegistry;

    @Inject
    public SLAMonitorProcessor(
        SLAMonitorStateStore slaMonitorStateStore,
        ExecutionStateStore executionStateStore,
        FlowMetaStoreInterface flowMetaStore,
        ExecutionService executionService,
        ExecutorService executorService,
        SLAService slaService,
        RunContextFactory runContextFactory,
        MetricRegistry metricRegistry) {
        this.slaMonitorStateStore = slaMonitorStateStore;
        this.executionStateStore = executionStateStore;
        this.flowMetaStore = flowMetaStore;
        this.executionService = executionService;
        this.executorService = executorService;
        this.slaService = slaService;
        this.runContextFactory = runContextFactory;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Processes every SLA monitor expired at {@code now} and returns the updated executor
     * contexts. The caller must emit the returned contexts only after this method returns —
     * never from inside the state-store transaction (see the class Javadoc).
     */
    public List<ExecutorContext> processExpired(Instant now) {
        List<ExecutorContext> executors = new ArrayList<>();

        slaMonitorStateStore.processExpired(
            now, slaMonitor -> process(slaMonitor).ifPresent(executors::add)
        );

        return executors;
    }

    private Optional<ExecutorContext> process(SLAMonitor slaMonitor) {
        return executionStateStore.lock(slaMonitor.getExecutionId(), execution ->
        {
            FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
            // null-safe: removing the LAST SLA from the flow leaves getSla() null, and an NPE
            // here would roll back processExpired() and wedge the whole SLA loop on the retry
            Optional<SLA> sla = ListUtils.emptyOnNull(flow.getSla()).stream().filter(s -> s.getId().equals(slaMonitor.getSlaId())).findFirst();
            if (sla.isEmpty()) {
                // this can happen in case the flow has been updated and the SLA removed
                log.debug("Cannot find the SLA '{}' in the flow for execution '{}', ignoring it.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                return null;
            }

            // There can be a race: a monitor can be found, but the execution terminated.
            // This particularly could occur in ElasticSearch due to refresh.
            if (executionService.isTerminated(flow, execution)) {
                return null;
            }

            metricRegistry
                .counter(MetricRegistry.METRIC_EXECUTOR_SLA_EXPIRED_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_EXPIRED_COUNT_DESCRIPTION, metricRegistry.tags(execution))
                .increment();

            ExecutorContext executor = new ExecutorContext(execution, flow);
            try {
                RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                Optional<Violation> violation = slaService.evaluateExecutionMonitoringSLA(runContext, executor.getExecution(), sla.get());
                if (violation.isPresent()) { // should always be true
                    log.info("Processing expired SLA monitor '{}' for execution '{}'.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                    executor = executorService.processViolation(runContext, executor, violation.get());

                    metricRegistry
                        .counter(
                            MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT_DESCRIPTION,
                            metricRegistry.tags(executor.getExecution())
                        )
                        .increment();
                }
            } catch (Exception e) {
                executor = executorService.handleFailedExecutionFromExecutor(executor, e);
            }

            return executor;
        });
    }
}
