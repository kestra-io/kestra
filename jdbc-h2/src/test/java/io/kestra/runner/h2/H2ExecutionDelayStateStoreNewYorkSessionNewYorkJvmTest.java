package io.kestra.runner.h2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:delay_new_york_session_new_york_jvm;LOCK_TIMEOUT=30000;TIME ZONE=America/New_York;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Execution(ExecutionMode.SAME_THREAD)
class H2ExecutionDelayStateStoreNewYorkSessionNewYorkJvmTest extends AbstractH2ExecutionDelayStateStoreTimezoneTest {

    @Inject
    H2ExecutionDelayStateStoreNewYorkSessionNewYorkJvmTest(ExecutionDelayStateStore executionDelayStateStore, JooqDSLContextWrapper dslContextWrapper) {
        super(executionDelayStateStore, dslContextWrapper);
    }

    @Override
    protected String sessionZone() {
        return "America/New_York";
    }

    @Test
    void shouldConsumeOnlyDueDelaysWhenPooledSessionIsNewYork() {
        assertOnlyDueDelaysAreConsumed("2031-01-15 09:00:00", "2031-01-15 11:00:00");
    }
}
