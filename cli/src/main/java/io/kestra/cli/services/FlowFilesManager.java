package io.kestra.cli.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.queues.QueueException;

public interface FlowFilesManager {

    FlowWithSource createOrUpdateFlow(GenericFlow flow) throws Exception;

    void deleteFlow(FlowWithSource toDelete) throws QueueException;

    void deleteFlow(String tenantId, String namespace, String id) throws QueueException;
}
