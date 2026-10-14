package io.kestra.core.scheduler.vnodes;

import java.util.function.Consumer;

import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.queue.SchedulerEventQueue;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;
import io.kestra.core.vnodes.VNodesAssignmentChannel;
import io.kestra.core.vnodes.VNodesAssignmentEvent;

/**
 * The vNodes of the schedulers, assigned through the {@link SchedulerEventQueue} with the {@link SchedulerEvent}
 * messages it has always carried, so that schedulers of different versions keep understanding each other.
 */
public class SchedulerVNodesAssignmentChannel implements VNodesAssignmentChannel {

    private final SchedulerEventQueue queue;

    public SchedulerVNodesAssignmentChannel(SchedulerEventQueue queue) {
        this.queue = queue;
    }

    @Override
    public ServiceType serviceType() {
        return ServiceType.SCHEDULER;
    }

    @Override
    public String controllerLockId() {
        return "controller";
    }

    @Override
    public void send(VNodesAssignmentEvent event) {
        queue.send(switch (event) {
            case VNodesAssignmentEvent.VNodesAssignmentRequest request -> new SchedulerEvent.VNodesAssignmentRequest(
                request.uid(), request.timestamp(), request.controllerId(), request.controllerEpoch(), request.services()
            );
            case VNodesAssignmentEvent.VNodesAssignmentReply reply -> new SchedulerEvent.VNodesAssignmentReply(
                reply.uid(), reply.timestamp(), reply.controllerId(), reply.controllerEpoch(), reply.serviceId()
            );
            case VNodesAssignmentEvent.VNodesAssignmentRelease release -> new SchedulerEvent.VNodesAssignmentRelease(
                release.uid(), release.timestamp(), release.controllerId(), release.controllerEpoch(), release.assignments()
            );
            case VNodesAssignmentEvent.VNodesAssignmentRejected rejected -> new SchedulerEvent.VNodesAssignmentRejected(
                rejected.uid(), rejected.timestamp(), rejected.controllerId(), rejected.controllerEpoch()
            );
        });
    }

    @Override
    public Disposable subscribe(Consumer<VNodesAssignmentEvent> consumer) {
        return queue.subscribe(event ->
        {
            VNodesAssignmentEvent assignmentEvent = switch (event) {
                case SchedulerEvent.VNodesAssignmentRequest request -> new VNodesAssignmentEvent.VNodesAssignmentRequest(
                    request.uid(), request.timestamp(), ServiceType.SCHEDULER, request.controllerId(), request.controllerEpoch(), request.schedulers()
                );
                case SchedulerEvent.VNodesAssignmentReply reply -> new VNodesAssignmentEvent.VNodesAssignmentReply(
                    reply.uid(), reply.timestamp(), ServiceType.SCHEDULER, reply.controllerId(), reply.controllerEpoch(), reply.schedulerId()
                );
                case SchedulerEvent.VNodesAssignmentRelease release -> new VNodesAssignmentEvent.VNodesAssignmentRelease(
                    release.uid(), release.timestamp(), ServiceType.SCHEDULER, release.controllerId(), release.controllerEpoch(), release.assignments()
                );
                case SchedulerEvent.VNodesAssignmentRejected rejected -> new VNodesAssignmentEvent.VNodesAssignmentRejected(
                    rejected.uid(), rejected.timestamp(), ServiceType.SCHEDULER, rejected.controllerId(), rejected.controllerEpoch()
                );
                default -> null;
            };
            if (assignmentEvent != null) {
                consumer.accept(assignmentEvent);
            }
        });
    }
}
