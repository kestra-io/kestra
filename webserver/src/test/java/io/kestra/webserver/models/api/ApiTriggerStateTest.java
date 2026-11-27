package io.kestra.webserver.models.api;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.Blocker;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTriggerStateTest {

    @Test
    void shouldExposeExecutionIdAndBlock() {
        Blocker block = new Blocker(Blocker.Reason.POLICY, List.of("deny-log"), "Log plugin is not allowed by policy");
        TriggerState state = TriggerState
            .of(TriggerId.of("tenant", "namespace", "flow", "trigger"), TriggerType.SCHEDULE, null, 0)
            .executionId(Clock.systemUTC(), "exec-123")
            .blockedBy(Clock.systemUTC(), block);

        ApiTriggerState dto = ApiTriggerState.from(state);

        assertThat(dto.executionId()).isEqualTo("exec-123");
        assertThat(dto.blockedBy()).isEqualTo(block);
    }
}
