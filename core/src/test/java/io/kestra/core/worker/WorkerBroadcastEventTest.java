package io.kestra.core.worker;

import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerBroadcastEventTest {

    @Test
    void shouldRoundTripMetadataChangeEvent() throws Exception {
        // Given
        MetadataChangePayload payload = new MetadataChangePayload(
            MetadataChangePayload.Type.NAMESPACE, "tenant-a", "prod.team");
        WorkerBroadcastEvent event = new WorkerBroadcastEvent.MetadataChangeEvent(payload);

        // When
        String json = JacksonMapper.ofJson().writeValueAsString(event);
        WorkerBroadcastEvent result = JacksonMapper.ofJson().readValue(json, WorkerBroadcastEvent.class);

        // Then
        assertThat(result).isInstanceOf(WorkerBroadcastEvent.MetadataChangeEvent.class);
        assertThat(((WorkerBroadcastEvent.MetadataChangeEvent) result).payload()).isEqualTo(payload);
    }

    @Test
    void shouldStillRoundTripExistingKillEvent() throws Exception {
        // Given
        ExecutionKilled killed = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .state(ExecutionKilled.State.EXECUTED)
            .build();
        WorkerBroadcastEvent event = new WorkerBroadcastEvent.KillEvent(killed);

        // When
        String json = JacksonMapper.ofJson().writeValueAsString(event);
        WorkerBroadcastEvent result = JacksonMapper.ofJson().readValue(json, WorkerBroadcastEvent.class);

        // Then
        assertThat(result).isInstanceOf(WorkerBroadcastEvent.KillEvent.class);
    }
}
