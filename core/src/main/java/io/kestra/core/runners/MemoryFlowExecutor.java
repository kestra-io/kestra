package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;

import java.util.Optional;

public class MemoryFlowExecutor implements FlowExecutorInterface {
    private FlowRepositoryInterface flowRepositoryInterface;

    public MemoryFlowExecutor(FlowRepositoryInterface flowRepositoryInterface) {
        this.flowRepositoryInterface = flowRepositoryInterface;
    }

    @Override
    public Flow findById(String namespace, String id, Optional<Integer> revision, String fromNamespace, String flowId) {
        return flowRepositoryInterface.findById(namespace, id, revision)
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "." + id + "' with revision + '" + revision + "'"));
    }
}
