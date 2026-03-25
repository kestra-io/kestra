package io.kestra.core.worker;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.server.ClusterEvent;

/**
 * Polymorphic event type for broadcasting events to gRPC workers.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = WorkerBroadcastEvent.KillEvent.class, name = "executionKilled"),
        @JsonSubTypes.Type(value = WorkerBroadcastEvent.ClusterBroadcast.class, name = "clusterEvent")
    }
)
public sealed interface WorkerBroadcastEvent {

    record KillEvent(ExecutionKilled payload) implements WorkerBroadcastEvent {
    }

    record ClusterBroadcast(ClusterEvent payload) implements WorkerBroadcastEvent {
    }
}
