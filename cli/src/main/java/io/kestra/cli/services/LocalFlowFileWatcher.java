package io.kestra.cli.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.PluginDefaultService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalFlowFileWatcher implements FlowFilesManager {
    private final FlowRepositoryInterface flowRepository;
    private final PluginDefaultService pluginDefaultService;

    public LocalFlowFileWatcher(FlowRepositoryInterface flowRepository, PluginDefaultService pluginDefaultService) {
        this.flowRepository = flowRepository;
        this.pluginDefaultService = pluginDefaultService;
    }

    @Override
    public FlowWithSource createOrUpdateFlow(Flow flow, String content) {
        FlowWithSource withDefault = pluginDefaultService.injectDefaults(FlowWithSource.of(flow, content));
        return flowRepository.findById(null, flow.getNamespace(), flow.getId())
            .map(previous -> flowRepository.update(flow, previous, content, withDefault))
            .orElseGet(() -> flowRepository.create(flow, content, withDefault));
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
