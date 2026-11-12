package io.kestra.scheduler.events;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.events.SchedulerEvent.VNodesAssignmentRequest;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ServiceType;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerEventTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test
    void shouldKeepTheSchedulerTypeNameWhenSerializingGivenASchedulerRebalance() throws JsonProcessingException {
        VNodesAssignmentRequest request = new VNodesAssignmentRequest(NOW, "controller", NOW, Set.of("scheduler-1"));

        String serialized = JacksonMapper.ofJson().writeValueAsString(request);

        assertThat(JacksonMapper.ofJson().readTree(serialized).get("type").asText()).isEqualTo("VNODES_ASSIGNMENT_REQUEST");
        assertThat(JacksonMapper.ofJson().readValue(serialized, SchedulerEvent.class)).isEqualTo(request);
    }

    @Test
    void shouldUseAServiceTypeNameWhenSerializingGivenAnExecutorRebalance() throws JsonProcessingException {
        VNodesAssignmentRequest request = new VNodesAssignmentRequest(NOW, "controller", NOW, Set.of("executor-1"), ServiceType.EXECUTOR);

        String serialized = JacksonMapper.ofJson().writeValueAsString(request);

        JsonNode json = JacksonMapper.ofJson().readTree(serialized);
        assertThat(json.get("type").asText()).isEqualTo("SERVICE_VNODES_ASSIGNMENT_REQUEST");
        assertThat(JacksonMapper.ofJson().readValue(serialized, SchedulerEvent.class)).isEqualTo(request);
    }
}
