package io.kestra.core.worker;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.vnodes.ControllerVNodesConfiguration;
import io.kestra.core.vnodes.VNodes;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Routes a worker job to one specific worker through a vNode of the controller holding it.
 * <p>
 * The controllers share vNodes assigned by the vNodes assignment protocol, and each one publishes in its liveness
 * properties the vNodes it was assigned ({@link #VNODES_PROP}) and the workers it holds ({@link #WORKERS_PROP}). A view
 * older than a rebalance or a reconnection only costs a forward from the controller that received the job.
 */
@Singleton
public class StickyWorkerRouting {

    public static final String WORKERS_PROP = "workers";
    public static final String VNODES_PROP = "vnodes";

    private static final String VNODE_ROUTING_KEY_PREFIX = "__vnode-";

    private final ServiceLivenessStore serviceLivenessStore;
    private final int vNodeCount;

    @Inject
    public StickyWorkerRouting(ServiceLivenessStore serviceLivenessStore, ControllerVNodesConfiguration configuration) {
        this.serviceLivenessStore = serviceLivenessStore;
        this.vNodeCount = configuration.count();
    }

    public static String routingKey(int vNode) {
        return VNODE_ROUTING_KEY_PREFIX + vNode;
    }

    /**
     * Builds the event sending the given job to the given worker. When no running controller is known to hold the
     * worker, the job goes to a vNode derived from the worker id, whose controller forwards it once the worker is found.
     */
    public WorkerJobEvent route(WorkerJob job, String workerQueueId, String workerId) {
        String routingKey = routingKeyForWorker(workerId)
            .orElseGet(() -> routingKey(VNodes.computeVNode(vNodeCount, workerId)));
        return new WorkerJobEvent(workerQueueId, job, new WorkerJobEvent.StickyRouting(workerId, routingKey));
    }

    /**
     * The workers of a controller are spread over its vNodes, so a vNode paused for a busy worker holds back only the
     * workers sharing it.
     */
    public Optional<String> routingKeyForWorker(String workerId) {
        return routingKeyForWorker(serviceLivenessStore.findAllInstancesInState(Service.ServiceState.RUNNING), workerId);
    }

    /**
     * Locates a worker with a single read of the liveness store. A worker that left the running states is lost: it
     * never runs again under the same id, and the liveness coordinator recovers its jobs.
     */
    public WorkerLocation locate(String workerId) {
        List<ServiceInstance> running = serviceLivenessStore.findAllInstancesInStates(Service.ServiceState.allRunningStates());
        Optional<String> routingKey = routingKeyForWorker(running, workerId);
        if (routingKey.isPresent()) {
            return new WorkerLocation.Held(routingKey.get());
        }
        return running.stream().anyMatch(instance -> instance.uid().equals(workerId)) ? new WorkerLocation.Unknown() : new WorkerLocation.Lost();
    }

    private static Optional<String> routingKeyForWorker(List<ServiceInstance> instances, String workerId) {
        return instances.stream()
            .filter(instance -> instance.is(ServiceType.CONTROLLER) && instance.is(Service.ServiceState.RUNNING))
            .filter(controller -> prop(controller, WORKERS_PROP).contains(workerId))
            .map(controller -> prop(controller, VNODES_PROP).stream().map(vNode -> ((Number) vNode).intValue()).sorted().toList())
            .filter(vNodes -> !vNodes.isEmpty())
            .findFirst()
            .map(vNodes -> routingKey(vNodes.get(VNodes.computeVNode(vNodes.size(), workerId))));
    }

    private static Collection<?> prop(ServiceInstance controller, String name) {
        if (controller.props() != null && controller.props().get(name) instanceof Collection<?> values) {
            return values;
        }
        return List.of();
    }

    public sealed interface WorkerLocation {

        record Held(String routingKey) implements WorkerLocation {
        }

        record Unknown() implements WorkerLocation {
        }

        record Lost() implements WorkerLocation {
        }
    }
}
