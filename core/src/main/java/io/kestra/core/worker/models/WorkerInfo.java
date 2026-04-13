package io.kestra.core.worker.models;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import io.kestra.core.runners.Worker;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import jakarta.inject.Inject;

/**
 * Provides information about the current worker.
 */
@Context
public class WorkerInfo {

    private final Supplier<String> workerId;

    @Inject
    public WorkerInfo(ApplicationContext applicationContext) {
        workerId = Suppliers.memoize(() -> applicationContext.getBean(Worker.class).getId());
    }

    /**
     * The worker unique id.
     * 
     * @return The worker id.
     */
    public String getWorkerId() {
        return workerId.get();
    }
}
