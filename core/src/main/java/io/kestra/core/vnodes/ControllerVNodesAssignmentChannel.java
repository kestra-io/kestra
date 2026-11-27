package io.kestra.core.vnodes;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.server.ServiceType;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The vNodes of the worker controllers.
 */
@Singleton
public class ControllerVNodesAssignmentChannel extends BroadcastVNodesAssignmentChannel {

    @Inject
    public ControllerVNodesAssignmentChannel(BroadcastQueueInterface<VNodesAssignmentEvent> queue) {
        super(queue, ServiceType.CONTROLLER, "worker-controller");
    }
}
