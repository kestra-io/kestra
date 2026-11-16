package io.kestra.core.vnodes;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.IdUtils;

/**
 * The messages of the vNodes assignment protocol, through which the elected {@link VNodeController}
 * assigns vNodes to the services of one {@link VNodesAssignmentChannel}. Several groups of services can share the
 * same queue: each message belongs to the group of its {@link #serviceType()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = VNodesAssignmentEvent.VNodesAssignmentRequest.class, name = "VNODES_ASSIGNMENT_REQUEST"),
        @JsonSubTypes.Type(value = VNodesAssignmentEvent.VNodesAssignmentReply.class, name = "VNODES_ASSIGNMENT_REPLY"),
        @JsonSubTypes.Type(value = VNodesAssignmentEvent.VNodesAssignmentRelease.class, name = "VNODES_ASSIGNMENT_RELEASE"),
        @JsonSubTypes.Type(value = VNodesAssignmentEvent.VNodesAssignmentRejected.class, name = "VNODES_ASSIGNMENT_REJECTED"),
    }
)
public sealed interface VNodesAssignmentEvent extends BroadcastEvent, HasUID {

    Instant timestamp();

    /**
     * @return the event type.
     */
    @JsonProperty
    default VNodesAssignmentEventType type() {
        return Enums.fromClassName(this, VNodesAssignmentEventType.class);
    }

    /**
     * @return the type of the services whose vNodes this message assigns.
     */
    ServiceType serviceType();

    /**
     * @return the identifier of the elected controller.
     */
    String controllerId();

    /**
     * @return when the controller was elected, so that services can ignore an outdated controller.
     */
    Instant controllerEpoch();

    @Override
    default String key() {
        return uid();
    }

    /**
     * Sent by the controller to start a rebalance among the given services.
     */
    record VNodesAssignmentRequest(String uid, Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch, Set<String> services) implements VNodesAssignmentEvent {

        public VNodesAssignmentRequest(Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch, Set<String> services) {
            this(IdUtils.create(), timestamp, serviceType, controllerId, controllerEpoch, services);
        }
    }

    /**
     * Sent by a service taking part in the rebalance started by a {@link VNodesAssignmentRequest}.
     */
    record VNodesAssignmentReply(String uid, Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch, String serviceId) implements VNodesAssignmentEvent {

        public VNodesAssignmentReply(Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch, String serviceId) {
            this(IdUtils.create(), timestamp, serviceType, controllerId, controllerEpoch, serviceId);
        }
    }

    /**
     * Sent by the controller with the vNodes of every service that replied.
     */
    record VNodesAssignmentRelease(String uid, Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch, Map<String, Set<Integer>> assignments) implements VNodesAssignmentEvent {

        public VNodesAssignmentRelease(Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch, Map<String, Set<Integer>> assignments) {
            this(IdUtils.create(), timestamp, serviceType, controllerId, controllerEpoch, assignments);
        }
    }

    /**
     * Sent by a service receiving a {@link VNodesAssignmentRequest} from an outdated controller.
     */
    record VNodesAssignmentRejected(String uid, Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch) implements VNodesAssignmentEvent {

        public VNodesAssignmentRejected(Instant timestamp, ServiceType serviceType, String controllerId, Instant controllerEpoch) {
            this(IdUtils.create(), timestamp, serviceType, controllerId, controllerEpoch);
        }
    }
}
