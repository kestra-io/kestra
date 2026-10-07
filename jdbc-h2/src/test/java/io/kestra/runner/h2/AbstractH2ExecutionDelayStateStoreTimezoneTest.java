package io.kestra.runner.h2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.AbstractExecutionDelayStateStoreTest;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractH2ExecutionDelayStateStoreTimezoneTest extends AbstractExecutionDelayStateStoreTest {

    // Pins the current DDL and must change with any fix to executordelayed."date".
    private static final Field<String> STORED_DATE = DSL.field("CAST(\"date\" AS VARCHAR)", String.class);

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    // The JVM zone must match the session zone the URL sets: jOOQ turns a LocalDateTime into a
    // Timestamp using the JVM zone, so a mismatched pair would hide a bad cutoff bind.
    protected void assertOnlyDueDelaysAreConsumed(String timezone, String expectedOverdueDate, String expectedFutureDate) {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        Instant now = Instant.parse("2031-01-15T10:00:00Z");
        List<ExecutionDelay> consumed = new ArrayList<>();

        try {
            dslContextWrapper.transaction(configuration ->
            {
                var context = DSL.using(configuration);
                store().save(delay("overdue", now.minusSeconds(3600)));
                store().save(delay("not-yet-due", now.plusSeconds(3600)));

                List<String> storedDates = context
                    .select(STORED_DATE)
                    .from(DSL.table("executordelayed"))
                    .orderBy(DSL.field(DSL.quotedName("date")))
                    .fetch(STORED_DATE);
                assertThat(storedDates).containsExactly(expectedOverdueDate, expectedFutureDate);

                store().processExpired(now, consumed::add);
            });

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
