package io.kestra.worker;

import io.kestra.core.models.flows.State;
import jakarta.inject.Singleton;

@Singleton
public class WorkerSecurityService {

    public State.Type callInSecurityContext(AbstractWorkerCallable callable) {
        return callable.call();
    }
}
