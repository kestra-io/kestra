package io.kestra.core.runners;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;
import jakarta.inject.Singleton;
import java.util.Objects;
import lombok.Setter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Singleton
public class DefaultFlowMetaStore implements FlowMetaStoreInterface {
    private final FlowRepositoryInterface flowRepository;

    @Setter
    private List<FlowWithSource> allFlows;

    public DefaultFlowMetaStore(FlowListenersInterface flowListeners, FlowRepositoryInterface flowRepository) {
        this.flowRepository = flowRepository;
        flowListeners.listen(flows -> allFlows = flows);
    }

    @Override
    public Collection<FlowWithSource> allLastVersion() {
        return this.allFlows;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        Optional<FlowInterface> find = this.allFlows
            .stream()
            .filter(flow -> ((flow.getTenantId() == null && tenantId == null) || Objects.equals(flow.getTenantId(), tenantId)) &&
                flow.getNamespace().equals(namespace) &&
                flow.getId().equals(id) &&
                (revision.isEmpty() || revision.get().equals(flow.getRevision()))
            )
            .map(it -> (FlowInterface)it)
            .findFirst();

        if (find.isPresent()) {
            return find;
        } else {
            return (Optional) flowRepository.findByIdWithSource(tenantId, namespace, id, revision);
        }
    }

    @Override
    public Boolean isReady() {
        return true;
    }
}
