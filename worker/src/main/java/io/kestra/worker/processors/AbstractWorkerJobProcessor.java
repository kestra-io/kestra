package io.kestra.worker.processors;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.trace.TraceUtils;
import io.kestra.core.trace.Tracer;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.processors.internals.AbstractWorkerCallable;
import io.opentelemetry.api.common.Attributes;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractWorkerJobProcessor<T extends WorkerJob> implements WorkerJobProcessor<T> {

    protected final String workerGroup;
    protected final MetricRegistry metricRegistry;

    private final WorkerSecurityService workerSecurityService;
    private final Tracer tracer;

    private final AtomicReference<WorkerJob> currentWorkerJob = new AtomicReference<>();
    private final AtomicReference<AbstractWorkerCallable> currentWorkerCallable = new AtomicReference<>();

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public AbstractWorkerJobProcessor(String workerGroup,
                                      MetricRegistry metricRegistry,
                                      WorkerSecurityService workerSecurityService,
                                      Tracer tracer) {
        this.workerGroup = workerGroup;
        this.tracer = tracer;
        this.metricRegistry = metricRegistry;
        this.workerSecurityService = workerSecurityService;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void process(final T job) {
        if (currentWorkerJob.compareAndSet(null, job)) {
            try {
                doProcess(job);
            } finally {
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
        try {
            return tracer.inCurrentContext(
                workerJobCallable.getRunContext(),
                workerJobCallable.getType(),
                Attributes.of(TraceUtils.ATTR_UID, workerJobCallable.getUid()),
                () -> workerSecurityService.callInSecurityContext(workerJobCallable)
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

    protected boolean isStopped() {
        return this.stopped.get();
    }
}
