package io.kestra.core.executor.command;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;

import io.kestra.core.events.EventId;
import io.kestra.core.models.HasUID;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.IdUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = Create.class, name = "CREATE"),
        @JsonSubTypes.Type(value = Replay.class, name = "REPLAY"),
        @JsonSubTypes.Type(value = ChangeTaskRunState.class, name = "CHANGE_TASK_RUN_STATE"),
        @JsonSubTypes.Type(value = ForceRun.class, name = "FORCE_RUN"),
        @JsonSubTypes.Type(value = Pause.class, name = "PAUSE"),
        @JsonSubTypes.Type(value = Restart.class, name = "RESTART"),
        @JsonSubTypes.Type(value = Resume.class, name = "RESUME"),
        @JsonSubTypes.Type(value = ResumeFromBreakpoint.class, name = "RESUME_FROM_BREAKPOINT"),
        @JsonSubTypes.Type(value = Unqueue.class, name = "UNQUEUE"),
        @JsonSubTypes.Type(value = UpdateLabels.class, name = "UPDATE_LABELS"),
        @JsonSubTypes.Type(value = UpdateStatus.class, name = "UPDATE_STATUS"),
        @JsonSubTypes.Type(value = ExecutionCommand.Invalid.class, name = "INVALID"),
    }
)
public interface ExecutionCommand extends HasUID, DispatchEvent, AsyncOperation {
    /**
     * @return the tenant id
     */
    String tenantId();

    /**
     * @return the namespace
     */
    String namespace();

    /**
     * @return the flow id
     */
    String flowId();

    /**
     * @return the execution id
     */
    String executionId();

    /**
     * @return the event timestamp.
     */
    Instant timestamp();

    /**
     * The event unique identifier.
     * <p>
     * Can be used to de-duplicate events or to correlate the event with an executor event.
     *
     * @return the event identifier.
     */
    EventId eventId();

    /**
     * @return the event type
     */
    @JsonProperty
    default ExecutionCommandType type() {
        return Enums.fromClassName(this, ExecutionCommandType.class);
    }

    @JsonIgnore
    @Override
    default String uid() {
        return IdUtils.fromParts(this.tenantId(), this.namespace(), this.flowId(), this.executionId());
    }

    @JsonIgnore
    @Override
    default String key() {
        return uid();
    }

    /**
     * Represents an invalid execution event.
     * Used for best effort deserialization of unexpected events due to serialization issue or removal of a supported event type.
     */
    record Invalid(String tenantId,
        String namespace,
        String flowId,
        String executionId,
        Instant timestamp,
        EventId eventId,
        Map<String, Object> properties) implements ExecutionCommand {

        @JsonCreator
        public Invalid(@JsonProperty("id") String tenantId,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("flowId") String flowId,
            @JsonProperty("executionId") String executionId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("eventId") EventId eventId) {
            this(tenantId, namespace, flowId, executionId, timestamp, eventId, new HashMap<>());
        }

        @JsonAnySetter
        public void addProperty(String key, Object value) {
            this.properties.put(key, value);
        }
    }
}
