package io.kestra.scheduler.stores;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.scheduler.SchedulerConfiguration;
import io.kestra.scheduler.vnodes.VNodes;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The  {@link FlowMetaStore} implementation.
 * <p>
 * Implementation based on the {@link FlowRepositoryInterface}.
 */
public class DefaultFlowMetaStore implements FlowMetaStore {
    
    private final SchedulerConfiguration schedulerConfiguration;
    private final FlowRepositoryInterface flowRepository;
    
    public DefaultFlowMetaStore(final SchedulerConfiguration schedulerConfiguration, final FlowRepositoryInterface flowRepository) {
        this.flowRepository = flowRepository;
        this.schedulerConfiguration = schedulerConfiguration;
    }
    
    /** {@inheritDoc} **/
    @Override
    public Optional<FlowWithSource> find(FlowId flowId) {
        return this.flowRepository.findByIdWithSourceWithoutAcl(flowId.getTenantId(), flowId.getNamespace(), flowId.getId(), Optional.ofNullable(flowId.getRevision()));
    }
    
    /** {@inheritDoc} **/
    @Override
    public List<FlowWithSource> findAllForVNodes(final Set<Integer> vNodes) {
        return this.flowRepository.findAllWithSourceForAllTenants()
            .stream()
            .filter(f -> vNodes.contains(VNodes.computeVNodeFromFlow(f, schedulerConfiguration.vnodes())))
            .toList();
    }
}
