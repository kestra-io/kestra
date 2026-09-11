package io.kestra.worker.processors;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.trace.TraceUtils;
import io.kestra.core.trace.Tracer;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.processors.internals.AbstractWorkerCallable;
import io.kestra.worker.services.ExecutionKilledManager;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

public abstract class AbstractWorkerJobProcessor<T extends WorkerJob> implements WorkerJobProcessor<T> {

    protected final String workerGroup;
    protected final MetricRegistry metricRegistry;
    protected final ExecutionKilledManager executionKilledManager;

    private final WorkerSecurityService workerSecurityService;
    private final Tracer tracer;

    private final AtomicReference<WorkerJob> currentWorkerJob = new AtomicReference<>();
    private final AtomicReference<AbstractWorkerCallable> currentWorkerCallable = new AtomicReference<>();

    // Bound once instead of passed as `this::interrupt` on every process() call: `this` never
    // changes across the many jobs a single processor instance handles, so re-capturing it as a
    // fresh lambda per call was pure allocation churn.
    private final Consumer<State.Type> interruptAction = this::interrupt;

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicReference<State.Type> pendingInterruptState = new AtomicReference<>();
    private final AtomicBoolean shutdownInterrupted = new AtomicBoolean(false);

    public AbstractWorkerJobProcessor(String workerGroup,
        MetricRegistry metricRegistry,
        WorkerSecurityService workerSecurityService,
        Tracer tracer,
        ExecutionKilledManager executionKilledManager) {
        this.workerGroup = workerGroup;
        this.tracer = tracer;
        this.metricRegistry = metricRegistry;
        this.workerSecurityService = workerSecurityService;
        this.executionKilledManager = executionKilledManager;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void process(final T job) {
        if (currentWorkerJob.compareAndSet(null, job)) {
            executionKilledManager.register(job.uid(), job, interruptAction);
            try {
                doProcess(job);
            } finally {
                executionKilledManager.unregister(job.uid());
                currentWorkerJob.set(null);
            }
        } else {
            // avoid miss-used of this class
            throw new ConcurrentModificationException("Processor can only process one job at a time.");
        }
    }

    protected abstract void doProcess(final T job);

    protected io.kestra.core.models.flows.State.Type callJob(AbstractWorkerCallable workerJobCallable) {
        this.currentWorkerCallable.set(workerJobCallable);
        // Propagate an interrupt that arrived before currentWorkerCallable was set
        // (between register() and here, interrupt() was a no-op).
        State.Type pendingState = pendingInterruptState.get();
        if (pendingState != null) {
            workerJobCallable.kill(pendingState);
        }
        try {
            return tracer.inCurrentContext(
                workerJobCallable.getRunContext(),
                workerJobCallable.getType(),
                Attributes.of(TraceUtils.ATTR_UID, workerJobCallable.getUid()),
                () ->
                {
                    var state = workerSecurityService.callInSecurityContext(workerJobCallable);
                    if (state != null && state.isTerminatedInError()) {
                        Span.current().setStatus(StatusCode.ERROR, "Task ended in state " + state.name());
                    }
                    return state;
                }
            );
        } catch (Exception e) {
            // should only occur if it fails in the tracing code which should be unexpected
            // we add the exception to have some log in that case
            workerJobCallable.setException(e);
            return State.Type.FAILED;
        } finally {
            this.currentWorkerCallable.set(null);
        }
    }

    @Override
    public void stop() {
        if (this.stopped.compareAndSet(false, true)) {
            Optional.ofNullable(currentWorkerCallable.get()).ifPresent(AbstractWorkerCallable::signalStop);
        }
    }

    @Override
    public void kill() {
        interrupt(State.Type.KILLED);
    }

    /**
     * Interrupts the currently running job, marking it to report {@code state} as its outcome.
     * Used both for a real kill ({@code KILLED}) and for a fail-fast interrupt targeting a
     * caller-chosen state (e.g. {@code CANCELLED}).
     */
    protected void interrupt(State.Type state) {
        pendingInterruptState.set(state);
        Optional.ofNullable(currentWorkerCallable.get()).ifPresent(callable -> callable.kill(state));
    }

    @Override
    public void signalShutdownInterrupt() {
        shutdownInterrupted.set(true);
        // interrupt() (not kill()) so the callable still reports FAILED rather than KILLED;
        // the shutdownInterrupted flag is what tells the processor to drop/defer the result.
        Optional.ofNullable(currentWorkerCallable.get()).ifPresent(AbstractWorkerCallable::interrupt);
    }

    protected boolean isStopped() {
        return this.stopped.get();
    }

    protected boolean isShutdownInterrupted() {
        return this.shutdownInterrupted.get();
    }
}
