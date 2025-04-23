package io.kestra.cli.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;

public interface FlowFilesManager {

    FlowWithSource createOrUpdateFlow(GenericFlow flow);

    void deleteFlow(FlowWithSource toDelete);

    void deleteFlow(String tenantId, String namespace, String id);
}
