package io.kestra.runner.h2;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

// The shipped H2 URL pins TIME ZONE=UTC (LocalCommand) while the JVM keeps the machine's zone, so
// the two zones differ on every dev box outside UTC.
@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:delay_utc_session_kolkata_jvm;LOCK_TIMEOUT=30000;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Execution(ExecutionMode.SAME_THREAD)
class H2ExecutionDelayStateStoreUtcSessionKolkataJvmTest extends AbstractH2ExecutionDelayStateStoreTimezoneTest {

    @Inject
    H2ExecutionDelayStateStoreUtcSessionKolkataJvmTest(ExecutionDelayStateStore executionDelayStateStore, JooqDSLContextWrapper dslContextWrapper) {
        super(executionDelayStateStore, dslContextWrapper);
    }

    @Override
    protected String sessionZone() {
        return "UTC";
    }

    @Override
    protected TimeZone testTimeZone() {
        return TimeZone.getTimeZone("Asia/Kolkata");
    }

    @Test
    void shouldConsumeOnlyDueDelaysWhenSessionIsUtcAndJvmIsNot() {
        assertOnlyDueDelaysAreConsumed("2031-01-15 09:00:00", "2031-01-15 11:00:00");
    }
}
