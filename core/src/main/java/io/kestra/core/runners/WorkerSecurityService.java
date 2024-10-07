package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import jakarta.inject.Singleton;

@Singleton
public class WorkerSecurityService {

    public State.Type callInSecurityContext(AbstractWorkerCallable callable) {
        return callable.call();
    }

    public boolean isInSecurityContext() {
        throw new UnsupportedOperationException();
    }

    public AbstractWorkerCallable getCallable() {
        throw new UnsupportedOperationException();
    }
}
