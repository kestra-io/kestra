package io.kestra.core.vnodes;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ServiceType;

import static org.assertj.core.api.Assertions.assertThat;

class VNodesAssignmentEventTest {

    @Test
    void shouldKeepItsTypeAndServiceTypeThroughSerialization() throws Exception {
        VNodesAssignmentEvent request = new VNodesAssignmentEvent.VNodesAssignmentRequest(Instant.parse("2026-01-01T00:00:00Z"), ServiceType.CONTROLLER, "server-1", Instant.parse("2026-01-01T00:00:00Z"), Set.of("controller-1"));

        String json = JacksonMapper.ofJson().writeValueAsString(request);

        assertThat(json).contains("\"type\":\"VNODES_ASSIGNMENT_REQUEST\"");
        assertThat(JacksonMapper.ofJson().readValue(json, VNodesAssignmentEvent.class)).isEqualTo(request);
    }

    @Test
    void shouldWriteTheSameTypeForTheSchedulerEvents() throws Exception {
        SchedulerEvent request = new SchedulerEvent.VNodesAssignmentRequest(Instant.now(), "server-1", Instant.now(), Set.of("scheduler-1"));

        String json = JacksonMapper.ofJson().writeValueAsString(request);

        assertThat(json).contains("\"type\":\"VNODES_ASSIGNMENT_REQUEST\"");
        assertThat(JacksonMapper.ofJson().readValue(json, SchedulerEvent.class)).isInstanceOf(SchedulerEvent.VNodesAssignmentRequest.class);
    }
}
