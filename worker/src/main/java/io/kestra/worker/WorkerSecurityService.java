package io.kestra.worker;

import io.kestra.core.models.flows.State;
import io.kestra.worker.processors.internals.AbstractWorkerCallable;

import jakarta.inject.Singleton;

@Singleton
public class WorkerSecurityService {

    public State.Type callInSecurityContext(AbstractWorkerCallable callable) {
        return callable.call();
    }
}
