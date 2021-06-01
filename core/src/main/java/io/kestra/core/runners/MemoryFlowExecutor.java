package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MemoryFlowExecutor implements FlowExecutorInterface {
    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Override
    public Flow findById(String namespace, String id, Optional<Integer> revision) {
        return flowRepositoryInterface.findById(namespace, id, revision)
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "." + id + "' with revision + '" + revision + "'"));
    }
}
