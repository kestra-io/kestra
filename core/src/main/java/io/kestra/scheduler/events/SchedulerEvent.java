package io.kestra.scheduler.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.utils.Enums;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SchedulerEvent.VNodesAssignmentRequest.class, name = "VNODES_ASSIGNMENT_REQUEST"),
    @JsonSubTypes.Type(value = SchedulerEvent.VNodesAssignmentReply.class, name = "VNODES_ASSIGNMENT_REPLY"),
    @JsonSubTypes.Type(value = SchedulerEvent.VNodesAssignmentRelease.class, name = "VNODES_ASSIGNMENT_RELEASE"),
    @JsonSubTypes.Type(value = SchedulerEvent.VNodesAssignmentRejected.class, name = "VNODES_ASSIGNMENT_REJECTED"),
})
public interface SchedulerEvent {
    
    /**
     * @return the event timestamp.
     */
    @JsonProperty
    Instant timestamp();
    
    /**
     * @return the event type.
     */
    @JsonProperty
    default SchedulerEventType type() {
        return Enums.fromClassName(this, SchedulerEventType.class);
    }
    
    /**
     * Interface for events part of the VNode assignment protocol.
     */
    interface VNodesAssignmentEvent extends SchedulerEvent {
        
        /**
         * The identifier of controller service.
         * 
         * @return controller identifier.
         */
        String controllerId();
        
        /**
         * The epoch timestamp when the Controller was elected.
         * <p>
         * This data can be used by Schedulers to make sure a received event is from the last elected controller.
         *
         * @return the {@link Instant}.
         */
        Instant controllerEpoch();
    }
    
    /**
     * Initial event sent by the Controller to trigger a new VNodes rebalancing.
     */
    record VNodesAssignmentRequest(
        Instant timestamp,
        String controllerId,
        Instant controllerEpoch,
        Set<String> schedulers
    ) implements VNodesAssignmentEvent {}
    
    /**
     * Event sent by a Scheduler in response to a {@link VNodesAssignmentRequest}.
     */
    record VNodesAssignmentReply(
        Instant timestamp,
        String controllerId,
        Instant controllerEpoch,
        String schedulerId
    ) implements VNodesAssignmentEvent {}
    
    /**
     * Event sent by the Controller to release the final VNode assignments to all schedulers
     * after collecting {@link VNodesAssignmentReply replies} from active schedulers.
     */
    record VNodesAssignmentRelease(
        Instant timestamp,
        String controllerId,
        Instant controllerEpoch,
        Map<String, Set<Integer>> assignments
    ) implements VNodesAssignmentEvent {
    }
    
    /**
     * Event sent by the Scheduler in response to a{@link VNodesAssignmentRequest} containing 
     * an outdated controller epoch.
     */
    record VNodesAssignmentRejected(
        Instant timestamp,
        String controllerId,
        Instant controllerEpoch
    ) implements VNodesAssignmentEvent {
    }
}
