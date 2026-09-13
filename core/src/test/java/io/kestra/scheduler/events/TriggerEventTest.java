package io.kestra.scheduler.events;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.events.ResetTrigger;
import io.kestra.core.scheduler.events.SetDisableTrigger;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerEventType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Enums;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TriggerEventTest {

    @Test
    void shouldSerializeEvent() throws JsonProcessingException {
        // Given
        TriggerId id = new TriggerId.Default("tenant", "namespace", "flow", "trigger");
        TriggerCreated event = new TriggerCreated(
            id,
            1,
            Instant.now().truncatedTo(ChronoUnit.MICROS), // the JSON format carries microseconds inside the queue/db
            EventId.create()
        );

        // When - then
        String serialized = JacksonMapper.ofJson().writeValueAsString(event);
        assertThat(JacksonMapper.ofJson().readValue(serialized, TriggerEvent.class)).isEqualTo(event);
    }

    @Test
    void shouldDeserializeSetDisableTriggerWithoutRecoverMissedSchedulesWhenPayloadIsFromOlderVersion() throws JsonProcessingException {
        // Given - a payload serialized by a version predating the recoverMissedSchedules field
        String serialized = """
            {
              "type": "SET_DISABLE_TRIGGER",
              "id": {"tenantId": "tenant", "namespace": "namespace", "flowId": "flow", "triggerId": "trigger"},
              "disabled": false,
              "timestamp": "2026-01-01T00:00:00Z",
              "eventId": "01942e9a-7b3c-7000-8000-000000000000"
            }
            """;

        // When
        TriggerEvent event = JacksonMapper.ofJson().readValue(serialized, TriggerEvent.class);

        // Then
        assertThat(event).isInstanceOf(SetDisableTrigger.class);
        assertThat(((SetDisableTrigger) event).recoverMissedSchedules()).isNull();
    }

    @Test
    void shouldGetTriggerEventType() {
        assertThat(Enums.fromClassName(new ResetTrigger(null), TriggerEventType.class)).isEqualTo(TriggerEventType.RESET_TRIGGER);
    }
}