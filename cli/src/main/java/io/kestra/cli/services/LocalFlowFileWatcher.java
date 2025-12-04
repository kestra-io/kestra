package io.kestra.cli.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.*;

@Slf4j
public class LocalFlowFileWatcher implements FlowFilesManager {
    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    public LocalFlowFileWatcher(FlowRepositoryInterface flowRepository, FlowService flowService) {
        this.flowRepository = flowRepository;
        this.flowService = flowService;
    }

    @Override
    public FlowWithSource createOrUpdateFlow(final GenericFlow flow) throws Exception {
        return flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId())
            .map(throwFunction(previous -> flowService.update(flow, previous)))
            .orElseGet(throwSupplier(() -> flowService.create(flow)));
    }

    @Override
    public void deleteFlow(FlowWithSource toDelete) throws QueueException {
        flowRepository.findByIdWithSource(toDelete.getTenantId(), toDelete.getNamespace(), toDelete.getId())
            .ifPresent(throwConsumer(flow -> flowService.delete(flow)));
        log.info("Flow {} has been deleted", toDelete.getId());
    }

    @Override
    public void deleteFlow(String tenantId, String namespace, String id) throws QueueException {
        flowRepository.findByIdWithSource(tenantId, namespace, id)
            .ifPresent(throwConsumer(flow -> flowService.delete(flow)));
        log.info("Flow {} has been deleted", id);
    }
}
