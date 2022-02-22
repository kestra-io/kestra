package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;

import java.util.Collection;
import java.util.Optional;

public class MemoryFlowExecutor implements FlowExecutorInterface {
    private final FlowRepositoryInterface flowRepositoryInterface;

    public MemoryFlowExecutor(FlowRepositoryInterface flowRepositoryInterface) {
        this.flowRepositoryInterface = flowRepositoryInterface;
    }

    @Override
    public Collection<Flow> allLastVersion() {
        return flowRepositoryInterface.findAll();
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        return flowRepositoryInterface.findById(namespace, id, revision);
    }
}
