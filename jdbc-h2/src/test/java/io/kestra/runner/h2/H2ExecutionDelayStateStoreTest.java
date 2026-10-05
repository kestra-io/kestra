package io.kestra.runner.h2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.AbstractExecutionDelayStateStoreTest;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:delay_asia_kolkata;LOCK_TIMEOUT=30000;TIME ZONE=Asia/Kolkata;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Execution(ExecutionMode.SAME_THREAD)
class H2ExecutionDelayStateStoreTest extends AbstractH2ExecutionDelayStateStoreTimezoneTest {

    @Test
    void shouldConsumeOnlyDueDelaysWhenPooledSessionIsAsiaKolkata() {
        assertOnlyDueDelaysAreConsumed("Asia/Kolkata", "2031-01-15 14:30:00", "2031-01-15 16:30:00");
    }
}

abstract class AbstractH2ExecutionDelayStateStoreTimezoneTest extends AbstractExecutionDelayStateStoreTest {

    private static final Field<String> STORED_DATE = DSL.field("CAST(\"date\" AS VARCHAR)", String.class);

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    protected void assertOnlyDueDelaysAreConsumed(String timezone, String expectedOverdueDate, String expectedFutureDate) {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        Instant now = Instant.parse("2031-01-15T10:00:00Z");
        List<ExecutionDelay> consumed = new ArrayList<>();

        try {
            dslContextWrapper.transaction(configuration -> {
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
