package io.kestra.runner.h2;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

// Before the digit-string cutoff bind, jOOQ built the bound Timestamp through Timestamp.valueOf,
// which read the UTC JVM zone, and H2 re-rendered it in the Kolkata session zone, so the
// comparison cutoff shifted.
@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:delay_kolkata_session_utc_jvm;LOCK_TIMEOUT=30000;TIME ZONE=Asia/Kolkata;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Execution(ExecutionMode.SAME_THREAD)
class H2ExecutionDelayStateStoreKolkataSessionUtcJvmTest extends AbstractH2ExecutionDelayStateStoreTimezoneTest {

    @Inject
    H2ExecutionDelayStateStoreKolkataSessionUtcJvmTest(ExecutionDelayStateStore executionDelayStateStore, JooqDSLContextWrapper dslContextWrapper) {
        super(executionDelayStateStore, dslContextWrapper);
    }

    @Override
    protected String sessionZone() {
        return "Asia/Kolkata";
    }

    @Override
    protected TimeZone testTimeZone() {
        return TimeZone.getTimeZone("UTC");
    }

    @Test
    void shouldConsumeOnlyDueDelaysWhenJvmZoneDiffersFromSessionZone() {
        assertOnlyDueDelaysAreConsumed("2031-01-15 09:00:00", "2031-01-15 11:00:00");
    }
}
