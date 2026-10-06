package io.kestra.runner.h2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.micronaut.context.annotation.Property;

@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:delay_asia_kolkata;LOCK_TIMEOUT=30000;TIME ZONE=Asia/Kolkata;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Execution(ExecutionMode.SAME_THREAD)
class H2ExecutionDelayStateStoreTest extends AbstractH2ExecutionDelayStateStoreTimezoneTest {

    @Test
    void shouldConsumeOnlyDueDelaysWhenPooledSessionIsAsiaKolkata() {
        assertOnlyDueDelaysAreConsumed("Asia/Kolkata", "2031-01-15 14:30:00", "2031-01-15 16:30:00");
    }
}
