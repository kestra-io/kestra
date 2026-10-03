package io.kestra.runner.h2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.AbstractExecutionDelayStateStoreTest;

import static org.assertj.core.api.Assertions.assertThat;

class H2ExecutionDelayStateStoreTest extends AbstractExecutionDelayStateStoreTest {

    @Test
    void shouldConsumeOnlyDueDelaysWhenJvmTimezoneIsAsiaKolkata() {
        assertOnlyDueDelaysAreConsumedWhenJvmTimezoneIs("Asia/Kolkata");
    }

    @Test
    void shouldConsumeOnlyDueDelaysWhenJvmTimezoneIsNewYork() {
        assertOnlyDueDelaysAreConsumedWhenJvmTimezoneIs("America/New_York");
    }

    private void assertOnlyDueDelaysAreConsumedWhenJvmTimezoneIs(String timezone) {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        try {
            Instant now = Instant.parse("2031-01-15T10:00:00Z");
            store().save(delay("overdue", now.minusSeconds(3600)));
            store().save(delay("not-yet-due", now.plusSeconds(3600)));

            List<ExecutionDelay> consumed = new ArrayList<>();
            store().processExpired(now, consumed::add);

            assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("overdue");
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static ExecutionDelay delay(String taskRunId, Instant date) {
        return ExecutionDelay.builder()
            .taskRunId(taskRunId)
            .executionId("execution-" + taskRunId)
            .date(date)
            .state(State.Type.RUNNING)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build();
    }
}
