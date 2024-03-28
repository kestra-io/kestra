package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;
import jakarta.inject.Singleton;
import lombok.Setter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Singleton
public class DefaultFlowExecutor implements FlowExecutorInterface {
    private final FlowRepositoryInterface flowRepositoryInterface;
    @Setter
    private List<Flow> allFlows;

    public DefaultFlowExecutor(FlowListenersInterface flowListeners, FlowRepositoryInterface flowRepositoryInterface) {
        this.flowRepositoryInterface = flowRepositoryInterface;
        flowListeners.listen(flows -> {
            this.allFlows = flows;
        });
    }

    @Override
    public Collection<Flow> allLastVersion() {
        return this.allFlows;
    }

    @Override
    public Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        Optional<Flow> find = this.allFlows
            .stream()
            .filter(flow -> ((flow.getTenantId() == null && tenantId == null) || flow.getTenantId().equals(tenantId)) &&
                flow.getNamespace().equals(namespace) &&
                flow.getId().equals(id) &&
                (revision.isEmpty() || revision.get().equals(flow.getRevision()))
            )
            .findFirst();

        if (find.isPresent()) {
            return find;
        } else {
            return flowRepositoryInterface.findById(tenantId, namespace, id, revision);
        }
    }

    @Override
    public Boolean isReady() {
        return true;
    }
}
