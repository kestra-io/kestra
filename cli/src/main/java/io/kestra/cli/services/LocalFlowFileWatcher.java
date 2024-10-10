package io.kestra.cli.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.context.annotation.Requires;
import lombok.extern.slf4j.Slf4j;

@Requires(property = "micronaut.io.watch.enabled", value = "true")
@Slf4j
public class LocalFlowFileWatcher implements FlowFilesManager {
    private FlowRepositoryInterface flowRepositoryInterface;

    public LocalFlowFileWatcher(FlowRepositoryInterface flowRepositoryInterface) {
        this.flowRepositoryInterface = flowRepositoryInterface;
    }

    public FlowWithSource createOrUpdateFlow(Flow flow, String content) {
        return flowRepositoryInterface.findById(null, flow.getNamespace(), flow.getId())
            .map(previous -> flowRepositoryInterface.update(flow, previous, content, flow))
            .orElseGet(() -> flowRepositoryInterface.create(flow, content, flow));
    }

    public void deleteFlow(FlowWithSource toDelete) {
        flowRepositoryInterface.findByIdWithSource(toDelete.getTenantId(), toDelete.getNamespace(), toDelete.getId()).ifPresent(flowRepositoryInterface::delete);
        log.debug("Flow {} has been deleted", toDelete.getId());
    }

    @Override
    public void deleteFlow(String tenantId, String namespace, String id) {
        flowRepositoryInterface.findByIdWithSource(tenantId, namespace, id).ifPresent(flowRepositoryInterface::delete);
        log.debug("Flow {} has been deleted", id);
    }
}
