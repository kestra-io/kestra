package io.kestra.runner.h2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.AbstractExecutionDelayStateStoreTest;
import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractH2ExecutionDelayStateStoreTimezoneTest extends AbstractExecutionDelayStateStoreTest {

    // Pins the current DDL and must change with any fix to executordelayed."date".
    private static final Field<String> STORED_DATE = DSL.field("CAST(\"date\" AS VARCHAR)", String.class);

    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public AbstractH2ExecutionDelayStateStoreTimezoneTest(ExecutionDelayStateStore executionDelayStateStore,
        JooqDSLContextWrapper dslContextWrapper) {
        super(executionDelayStateStore);
        this.dslContextWrapper = Objects.requireNonNull(dslContextWrapper);
    }

    private TimeZone originalTimeZone;

    // The JVM zone must match the session zone the datasource URL sets, because jOOQ converts the
    // cutoff with Timestamp.valueOf, which reads the JVM zone. A mismatch would hide a bad bind, so
    // the zone is set for every test in the subclass, not only the timezone-specific one.
    // JUnit always runs a superclass @BeforeEach before a subclass one, so the zone is set from the
    // hook the contract calls at the start of its own drain instead of from a callback of our own.
    @Override
    protected void prepare() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(sessionZone()));
    }

    @Override
    protected void cleanup() {
        TimeZone.setDefault(originalTimeZone);
    }

    /** Must match the TIME ZONE= of this class' datasource URL. */
    protected abstract String sessionZone();

    protected void assertOnlyDueDelaysAreConsumed(String expectedOverdueDate, String expectedFutureDate) {
        Instant now = Instant.parse("2031-01-15T10:00:00Z");
        List<ExecutionDelay> consumed = new ArrayList<>();

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
