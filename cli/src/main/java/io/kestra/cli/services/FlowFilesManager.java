package io.kestra.cli.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;

public interface FlowFilesManager {

    FlowWithSource createOrUpdateFlow(Flow flow, String content);

    void deleteFlow(FlowWithSource toDelete);

    void deleteFlow(String tenantId, String namespace, String id);
}
