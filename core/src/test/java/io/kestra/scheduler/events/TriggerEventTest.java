package io.kestra.scheduler.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Enums;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TriggerEventTest {
    
    @Test
    void shouldSerializeEvent() throws JsonProcessingException {
        // Given
        TriggerId id = new TriggerId.Default("tenant", "namespace", "flow", "trigger");
        TriggerCreated event = new TriggerCreated(id, Instant.now(), 1);
        
        // When - then
        String serialized = JacksonMapper.ofJson().writeValueAsString(event);
        assertThat(JacksonMapper.ofJson().readValue(serialized, TriggerEvent.class)).isEqualTo(event);
    }
    
    @Test
    void shouldGetTriggerEventType() {
        assertThat(Enums.fromClassName(new ResetTrigger(null, null), TriggerEventType.class)).isEqualTo(TriggerEventType.RESET_TRIGGER);
    }
}