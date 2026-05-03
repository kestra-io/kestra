package io.kestra.core.scheduler.events;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.kestra.core.events.EventId;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.event.VNodeDispatchEvent;
import io.kestra.core.utils.Enums;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = TriggerCreated.class, name = "TRIGGER_CREATED"),
        @JsonSubTypes.Type(value = TriggerUpdated.class, name = "TRIGGER_UPDATED"),
        @JsonSubTypes.Type(value = TriggerFlowRevisionUpdated.class, name = "TRIGGER_FLOW_REVISION_UPDATED"),
        @JsonSubTypes.Type(value = TriggerDeleted.class, name = "TRIGGER_DELETED"),
        @JsonSubTypes.Type(value = TriggerEvaluated.class, name = "TRIGGER_EVALUATED"),
        @JsonSubTypes.Type(value = TriggerExecutionTerminated.class, name = "TRIGGER_EXECUTION_TERMINATED"),
        @JsonSubTypes.Type(value = CreateBackfillTrigger.class, name = "CREATE_BACKFILL_TRIGGER"),
        @JsonSubTypes.Type(value = DeleteBackfillTrigger.class, name = "DELETE_BACKFILL_TRIGGER"),
        @JsonSubTypes.Type(value = SetPauseBackfillTrigger.class, name = "SET_PAUSE_BACKFILL_TRIGGER"),
        @JsonSubTypes.Type(value = ResetTrigger.class, name = "RESET_TRIGGER"),
        @JsonSubTypes.Type(value = SetDisableTrigger.class, name = "SET_DISABLE_TRIGGER"),
        @JsonSubTypes.Type(value = TriggerReceived.class, name = "TRIGGER_RECEIVED"),
        @JsonSubTypes.Type(value = TriggerEvent.Invalid.class, name = "INVALID"),
    }
)
public interface TriggerEvent extends VNodeDispatchEvent, HasUID {

    /**
     * @return the trigger identifier.
     */
    @JsonProperty
    @JsonDeserialize(as = TriggerId.Default.class)
    TriggerId id();

    /**
     * @return the event timestamp.
     */
    @JsonProperty
    Instant timestamp();

    /**
     * The event unique identifier.
     * <p>
     * Can be used to de-duplicate events.
     *
     * @return the event identifier.
     */
    @JsonProperty
    EventId eventId();

    /**
     * @return the event type.
     */
    @JsonProperty
    default TriggerEventType type() {
        return Enums.fromClassName(this, TriggerEventType.class);
    }

    /**
     * {@inheritDoc}
     */
    @JsonIgnore
    @Override
    default String uid() {
        return this.id().uid();
    }

    /**
     * The key used for vNode dispatching.
     *
     * @return the routing key.
     */
    @Override
    default String key() {
        return FlowId.uidWithoutRevision(FlowId.of(id().getTenantId(), id().getNamespace(), id().getFlowId(), null));
    }

    record Invalid(TriggerId id,
        Instant timestamp,
        EventId eventId,
        Map<String, Object> properties) implements TriggerEvent {

        @JsonCreator
        public Invalid(@JsonProperty("id") TriggerId id,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("eventId") EventId eventId) {
            this(id, timestamp, eventId, new HashMap<>());
        }

        @JsonAnySetter
        public void addProperty(String key, Object value) {
            this.properties.put(key, value);
        }
    }
}
