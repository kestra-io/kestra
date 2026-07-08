package io.kestra.scheduler.stores;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.vnodes.VNodes;

/**
 * The {@link FlowMetaStore} implementation.
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
        // Without an explicit revision the scheduler operates on the latest non-draft revision
        // so saving a draft does not silently disable existing schedules; with an explicit
        // revision (existing trigger state already bound to it) we honor the bound version.
        if (flowId.getRevision() == null) {
            return this.flowRepository.findByIdWithSourceForExecutionWithoutAcl(flowId.getTenantId(), flowId.getNamespace(), flowId.getId());
        }
        return this.flowRepository.findByIdWithSourceWithoutAcl(flowId.getTenantId(), flowId.getNamespace(), flowId.getId(), Optional.of(flowId.getRevision()));
    }

    /** {@inheritDoc} **/
    @Override
    public List<FlowWithSource> findAllForVNodes(final Set<Integer> vNodes) {
        return this.flowRepository.findAllWithSourceForExecutionForAllTenants()
            .stream()
            .filter(f -> vNodes.contains(VNodes.computeVNodeFromFlow(f, schedulerConfiguration.vnodes())))
            .toList();
    }
}
