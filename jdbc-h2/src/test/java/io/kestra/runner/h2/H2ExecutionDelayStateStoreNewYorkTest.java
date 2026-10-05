package io.kestra.runner.h2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.micronaut.context.annotation.Property;

@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:delay_new_york;LOCK_TIMEOUT=30000;TIME ZONE=America/New_York;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Execution(ExecutionMode.SAME_THREAD)
class H2ExecutionDelayStateStoreNewYorkTest extends AbstractH2ExecutionDelayStateStoreTimezoneTest {

    @Test
    void shouldConsumeOnlyDueDelaysWhenPooledSessionIsNewYork() {
        assertOnlyDueDelaysAreConsumed("America/New_York", "2031-01-15 04:00:00", "2031-01-15 06:00:00");
    }
}
