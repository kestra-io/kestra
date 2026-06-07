package io.kestra.webserver.models.api;

import java.time.Clock;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTriggerStateTest {

    @Test
    void shouldExposeExecutionId() {
        TriggerState state = TriggerState
            .of(TriggerId.of("tenant", "namespace", "flow", "trigger"), TriggerType.SCHEDULE, null, false, 0)
            .executionId(Clock.systemUTC(), "exec-123");

        ApiTriggerState dto = ApiTriggerState.from(state);

        assertThat(dto.executionId()).isEqualTo("exec-123");
    }
}
