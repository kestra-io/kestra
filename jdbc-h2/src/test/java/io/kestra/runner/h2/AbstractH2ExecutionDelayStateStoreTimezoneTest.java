package io.kestra.runner.h2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.executor.ExecutionDelayStateStoreContract;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractH2ExecutionDelayStateStoreTimezoneTest extends ExecutionDelayStateStoreContract {

    // Pins the current DDL; update with any fix to executordelayed."date".
    private static final Field<String> STORED_DATE = DSL.field("CAST(\"date\" AS VARCHAR)", String.class);

    // Only this contract's rows are asserted, so a row another suite owns cannot break the count.
    private static final Condition OWNED_ROWS = DSL.field(DSL.quotedName("key"), String.class)
        .like(OWNED_EXECUTION_ID_PREFIX + "%");

    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public AbstractH2ExecutionDelayStateStoreTimezoneTest(ExecutionDelayStateStore executionDelayStateStore, JooqDSLContextWrapper dslContextWrapper) {
        super(executionDelayStateStore);
        this.dslContextWrapper = Objects.requireNonNull(dslContextWrapper);
    }

    @Override
    protected TimeZone testTimeZone() {
        return TimeZone.getTimeZone(sessionZone());
    }

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
                .where(OWNED_ROWS)
                .orderBy(DSL.field(DSL.quotedName("date")))
                .fetch(STORED_DATE);
            assertThat(storedDates).containsExactly(expectedOverdueDate, expectedFutureDate);

            consumed.addAll(drainOwnedDelays(now));
        });

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("overdue");
    }
}
