package io.kestra.core.worker.models;

import java.util.concurrent.atomic.AtomicBoolean;

import io.kestra.core.utils.IdUtils;

import io.micronaut.context.annotation.Context;

/**
 * Provides information about the current worker.
 */
@Context
@io.micronaut.context.annotation.Requires(beans = Worker.class)
public class WorkerInfo {

    private final String workerId = IdUtils.create();
    private final AtomicBoolean workerIdClaimed = new AtomicBoolean();

    /**
     * The worker unique id.
     *
     * @return The worker id.
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Hands the worker id to the first worker built in this context, and a fresh id to any later one, so that two workers started side by side (as tests do) never register under the same id.
     *
     * @return The id the calling worker must identify itself with.
     */
    public String claimWorkerId() {
        return workerIdClaimed.compareAndSet(false, true) ? workerId : IdUtils.create();
    }
}
