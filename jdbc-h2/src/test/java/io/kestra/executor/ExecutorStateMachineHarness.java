package io.kestra.executor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultFlowMetaStore;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.WorkerTaskResult;

/**
 * Drives the real {@link DefaultExecutor} as a state machine over real H2, one external event at a
 * time, with no runner/worker/scheduler/threads. {@code process} enters with an execution (and
 * optionally an external event), advances the executor until it either terminates or parks waiting
 * for the outside world (a worker task, a subflow, ...), and returns the execution persisted in H2.
 *
 * <p>Settling drains only the executor's own {@code executionEventQueue} follow-ups; every other
 * emitted message (worker jobs, subflow results, released queued executions, ...) is a boundary the
 * test asserts on via {@link #emitted(Class)} and drives explicitly with the next {@code process}.
 *
 * <p>One harness per test carries one unique, non-null tenant; a fixture with a different or missing
 * tenant is rejected, because the H2 file is shared and never cleaned.
 */
public class ExecutorStateMachineHarness {
    private static final int MAX_CYCLES = 100;

    private final DefaultExecutor executor;
    private final FlowRepositoryInterface flowRepository;
    private final DefaultFlowMetaStore flowMetaStore;
    private final ExecutionRepositoryInterface executionRepository;
    private final ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;
    private final QueueRecorder recorder;
    private final String tenantId;
    private final Set<String> registeredFlows = new HashSet<>();

    ExecutorStateMachineHarness(
        DefaultExecutor executor,
        FlowRepositoryInterface flowRepository,
        DefaultFlowMetaStore flowMetaStore,
        ExecutionRepositoryInterface executionRepository,
        ConcurrencyLimitRepositoryInterface concurrencyLimitRepository,
        QueueRecorder recorder,
        String tenantId
    ) {
        this.executor = executor;
        this.flowRepository = flowRepository;
        this.flowMetaStore = flowMetaStore;
        this.executionRepository = executionRepository;
        this.concurrencyLimitRepository = concurrencyLimitRepository;
        this.recorder = recorder;
        this.tenantId = tenantId;
    }

    public String tenantId() {
        return tenantId;
    }

    /** Running-execution count the flow's concurrency limit is holding, read from the real store. */
    public int concurrencyRunning(Flow flow) {
        return concurrencyLimitRepository.findById(tenantId, flow.getNamespace(), flow.getId())
            .map(limit -> limit.getRunning() == null ? 0 : limit.getRunning())
            .orElse(0);
    }

    /** Advance {@code execution} through the executor's own cycles (start it, or resume it). */
    public Execution process(Flow flow, Execution execution) {
        prepare(flow, execution);
        recorder.reset();
        ExecutionEventType type = execution.getState().getCurrent() == State.Type.CREATED
            ? ExecutionEventType.CREATED
            : ExecutionEventType.UPDATED;
        executor.onExecutionEvent(new ExecutionEvent(execution, type));
        return settle(execution.getId());
    }

    /** Apply a worker task result to {@code execution}, then advance it. */
    public Execution process(Flow flow, Execution execution, WorkerTaskResult workerTaskResult) {
        prepare(flow, execution);
        recorder.reset();
        executor.onWorkerTaskResult(workerTaskResult);
        return settle(execution.getId());
    }

    public <T> List<T> emitted(Class<T> type) {
        return recorder.emitted(type);
    }

    /**
     * Advance the executor through its own follow-up cycles until the execution terminates or parks
     * waiting for the outside world. After each cycle the executor has emitted, for this execution,
     * either nothing (parked — e.g. a task is out with a worker), one {@code UPDATED} event (it
     * advanced and wants to run again), or one {@code TERMINATED} event (it is done). We feed the
     * {@code UPDATED} events back in; every other emit (worker jobs, subflow results, ...) is a
     * boundary left in the recorder for the test to assert on and drive with the next {@code process}.
     */
    private Execution settle(String executionId) {
        int fed = 0;
        for (int cycle = 0; cycle < MAX_CYCLES; cycle++) {
            List<ExecutionEvent> selfEvents = recorder.emitted(ExecutionEvent.class).stream()
                .filter(event -> executionId.equals(event.executionId()))
                .toList();
            if (selfEvents.size() == fed) {
                return reload(executionId); // nothing new since the last cycle: terminated or parked
            }
            ExecutionEvent next = selfEvents.get(fed++);
            if (next.eventType() == ExecutionEventType.TERMINATED) {
                return reload(executionId);
            }
            executor.onExecutionEvent(next); // UPDATED: run one more cycle
        }
        throw new IllegalStateException(
            "Executor did not settle within %d cycles for execution '%s'.".formatted(MAX_CYCLES, executionId)
        );
    }

    private void prepare(Flow flow, Execution execution) {
        requireTenant(flow.getTenantId(), "flow '%s'".formatted(flow.getId()));
        requireTenant(execution.getTenantId(), "execution '%s'".formatted(execution.getId()));
        registerFlow(flow);
        if (executionRepository.findById(tenantId, execution.getId()).isEmpty()) {
            executionRepository.save(execution);
        }
    }

    private void requireTenant(String candidate, String what) {
        if (!tenantId.equals(candidate)) {
            throw new IllegalArgumentException(
                "The %s must use the harness tenant '%s' but was '%s'; build fixtures with harness.tenantId().".formatted(what, tenantId, candidate)
            );
        }
    }

    /** Make {@code flow} resolvable by the executor (e.g. a flow-trigger target that no execution enters directly). */
    public ExecutorStateMachineHarness registerFlow(Flow flow) {
        requireTenant(flow.getTenantId(), "flow '%s'".formatted(flow.getId()));
        String key = "%s/%s/%s".formatted(flow.getNamespace(), flow.getId(), flow.getRevision());
        if (registeredFlows.add(key)) {
            flowRepository.create(GenericFlow.of(flow));
            // the executor resolves flow triggers from the meta-store cache, which is populated
            // asynchronously in production; refresh it here since the harness runs no listeners.
            flowMetaStore.reload();
        }
        return this;
    }

    private Execution reload(String executionId) {
        return executionRepository.findById(tenantId, executionId).orElseThrow();
    }
}
