package io.kestra.cli.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalFlowFileWatcher implements FlowFilesManager {
    private final FlowRepositoryInterface flowRepository;

    public LocalFlowFileWatcher(FlowRepositoryInterface flowRepository) {
        this.flowRepository = flowRepository;
    }

    @Override
    public FlowWithSource createOrUpdateFlow(final GenericFlow flow) {
        return flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId())
            .map(previous -> flowRepository.update(flow, previous))
            .orElseGet(() -> flowRepository.create(flow));
    }

    @Override
    public void deleteFlow(FlowWithSource toDelete) {
        flowRepository.findByIdWithSource(toDelete.getTenantId(), toDelete.getNamespace(), toDelete.getId()).ifPresent(flowRepository::delete);
        log.info("Flow {} has been deleted", toDelete.getId());
    }

    @Override
    public void deleteFlow(String tenantId, String namespace, String id) {
        flowRepository.findByIdWithSource(tenantId, namespace, id).ifPresent(flowRepository::delete);
        log.info("Flow {} has been deleted", id);
    }
}
